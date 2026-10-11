package vn.ticketscenter.payment.repository;

import vn.ticketscenter.payment.service.PaymentException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PaymentRepository {
    private final DataSource buyer;

    public PaymentRepository(DataSource buyer) {
        this.buyer = Objects.requireNonNull(buyer, "buyer DataSource is required");
    }

    public record PaymentIntent(
            UUID paymentId,
            String txnRef,
            BigDecimal amount,
            String currency,
            String orderCode,
            Duration holdRemaining
    ) { }

    public static final class ZeroOrderDetectedException extends RuntimeException {
        private final UUID orderId;
        public ZeroOrderDetectedException(UUID orderId) {
            super("Order is 0 VND");
            this.orderId = orderId;
        }
        public UUID orderId() { return orderId; }
    }

    public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        // 1. Kiểm tra chi tiết đơn hàng trước (order_code, total_amount, hold remaining)
        String orderCode;
        BigDecimal totalAmount;
        Duration holdRemaining;
        try (Connection conn = buyer.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT o.order_code, o.total_amount, h.expires_at, o.status, h.status as hold_status, e.status as event_status
                     FROM dbo.tc_orders o WITH (NOLOCK)
                     JOIN dbo.tc_ticket_holds h WITH (NOLOCK) ON h.id = o.hold_id
                     JOIN dbo.tc_events e WITH (NOLOCK) ON e.id = o.event_id
                     WHERE o.id = ? AND o.user_id = ?
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw PaymentException.orderNotFound();
                }
                String ordStatus = rs.getString("status");
                String holdStatus = rs.getString("hold_status");
                String evtStatus = rs.getString("event_status");
                Timestamp expiresAt = rs.getTimestamp("expires_at");

                if (!"PENDING_PAYMENT".equals(ordStatus) || !"ACTIVE".equals(holdStatus)) {
                    throw PaymentException.orderNotPayable();
                }
                if (!"PUBLISHED".equals(evtStatus)) {
                    throw PaymentException.eventUnavailable();
                }

                long remainingMs = expiresAt.getTime() - System.currentTimeMillis();
                if (remainingMs <= 0) {
                    throw PaymentException.orderNotPayable();
                }
                holdRemaining = Duration.ofMillis(remainingMs);
                orderCode = rs.getString("order_code");
                totalAmount = rs.getBigDecimal("total_amount");
            }
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }

        // Nếu tổng tiền là 0 VNĐ ➔ ném ZeroOrderDetectedException để xử lý luồng zero order
        if (totalAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new ZeroOrderDetectedException(orderId);
        }

        // 2. Gọi procedure usp_BeginOrderPayment
        return call("EXEC dbo.usp_BeginOrderPayment ?, ?", rows -> {
            UUID pid = (UUID) rows.getObject("payment_id");
            String txn = rows.getString("txn_ref");
            BigDecimal amt = rows.getBigDecimal("amount");
            String curr = rows.getString("currency").trim();
            return new PaymentIntent(pid, txn, amt, curr, orderCode, holdRemaining);
        }, orderId, actorId);
    }

    public void completeZeroOrder(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        String verifiedResult = "{\"status\":\"CAPTURED\",\"providerReference\":\"ZERO_PAYMENT\"}";
        try (Connection conn = buyer.getConnection();
             CallableStatement cs = conn.prepareCall("{call dbo.usp_ApplyPaymentResult(?, ?, ?, ?, ?)}")) {
            cs.setObject(1, orderId);
            cs.setObject(2, actorId);
            cs.setNull(3, Types.OTHER);
            cs.setString(4, verifiedResult);
            cs.setString(5, "[]");
            cs.execute();
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    public Optional<PaymentIntent> findExistingPayment(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        try (Connection conn = buyer.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT TOP 1 p.id, p.txn_ref, p.amount, o.order_code, h.expires_at
                     FROM dbo.tc_payments p WITH (NOLOCK)
                     JOIN dbo.tc_orders o WITH (NOLOCK) ON o.id = p.order_id
                     JOIN dbo.tc_ticket_holds h WITH (NOLOCK) ON h.id = o.hold_id
                     WHERE p.order_id = ? AND o.user_id = ? AND p.status IN ('PENDING', 'UNKNOWN')
                     ORDER BY p.created_at DESC
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID pid = (UUID) rs.getObject("id");
                String txn = rs.getString("txn_ref");
                BigDecimal amt = rs.getBigDecimal("amount");
                String code = rs.getString("order_code");
                Timestamp expires = rs.getTimestamp("expires_at");
                Duration remaining = Duration.ofMillis(Math.max(1000, expires.getTime() - System.currentTimeMillis()));
                return Optional.of(new PaymentIntent(pid, txn, amt, "VND", code, remaining));
            }
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    @FunctionalInterface private interface Read<T> { T read(ResultSet rows) throws SQLException; }

    private <T> T call(String sql, Read<T> read, Object... args) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Connection connection = buyer.getConnection()) {
                connection.setAutoCommit(true);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(20);
                    for (int i = 0; i < args.length; i++) {
                        statement.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
                    }
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw PaymentException.unavailable();
                        return read.read(rows);
                    }
                }
            } catch (SQLException failure) {
                PaymentException mapped = domain(failure);
                if (mapped != null) throw mapped;
                if (attempt == 2 || !retryable(failure)) throw PaymentException.unavailable();
            }
        }
        throw PaymentException.unavailable();
    }

    private static boolean retryable(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            if (error.getErrorCode() == 1205 || error.getErrorCode() == 1222) return true;
            String state = error.getSQLState();
            if (state != null && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01"))) return true;
        }
        return false;
    }

    private static PaymentException domain(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            PaymentException mapped = switch (error.getErrorCode()) {
                case 51280, 51395 -> PaymentException.orderNotPayable();
                case 51281 -> PaymentException.eventUnavailable();
                case 51282 -> null; // Handled as zero order
                case 51392 -> PaymentException.orderNotFound();
                case 51393 -> PaymentException.forbidden();
                case 51232, 51389 -> PaymentException.paymentUnresolved();
                default -> null;
            };
            if (mapped != null) return mapped;
        }
        return null;
    }
}
