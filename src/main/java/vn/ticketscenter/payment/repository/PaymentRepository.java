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
    private final DataSource worker;

    public PaymentRepository(DataSource buyer) {
        this(buyer, buyer);
    }

    public PaymentRepository(DataSource buyer, DataSource worker) {
        this.buyer = Objects.requireNonNull(buyer, "buyer DataSource is required");
        this.worker = Objects.requireNonNull(worker, "worker DataSource is required");
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
        return beginOrderPayment(orderId, actorId, -1);
    }

    public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        // 1. Kiểm tra chi tiết đơn hàng trước (order_code, total_amount, hold remaining)
        String orderCode;
        BigDecimal totalAmount;
        Duration holdRemaining;
        try (Connection conn = buyer.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT order_code,total_amount,expires_at,order_status AS status,hold_status,event_status
                     FROM dbo.vw_PaymentOrderContext WHERE order_id=? AND user_id=?
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
                Timestamp expiresAt = rs.getTimestamp("expires_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));

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
            UUID pid = UUID.fromString(rows.getString("payment_id"));
            String txn = rows.getString("txn_ref");
            BigDecimal amt = rows.getBigDecimal("amount");
            String curr = rows.getString("currency").trim();
            return new PaymentIntent(pid, txn, amt, curr, orderCode, holdRemaining);
        }, orderId, actorId);
    }

    public void completeZeroOrder(UUID orderId, UUID actorId) {
        completeZeroOrder(orderId, actorId, -1);
    }

    public void completeZeroOrder(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        String ticketsJson = generateTicketsForOrder(orderId, buyer);
        try (Connection conn = buyer.getConnection();
             CallableStatement cs = conn.prepareCall("{call dbo.usp_CompleteFreeOrder(?, ?, ?)}")) {
            cs.setObject(1, orderId);
            cs.setObject(2, actorId);
            cs.setString(3, ticketsJson);
            cs.execute();
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    public Optional<PaymentIntent> findExistingPayment(UUID orderId, UUID actorId) {
        return findExistingPayment(orderId, actorId, -1);
    }

    public Optional<PaymentIntent> findExistingPayment(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        try (Connection conn = buyer.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT TOP 1 payment_id AS id,txn_ref,amount,order_code,expires_at
                     FROM dbo.vw_PaymentRuntimeDetails
                     WHERE order_id=? AND user_id=? AND payment_status IN ('PENDING','UNKNOWN')
                     ORDER BY created_at DESC
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID pid = UUID.fromString(rs.getString("id"));
                String txn = rs.getString("txn_ref");
                BigDecimal amt = rs.getBigDecimal("amount");
                String code = rs.getString("order_code");
                Timestamp expires = rs.getTimestamp("expires_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
                Duration remaining = Duration.ofMillis(Math.max(1000, expires.getTime() - System.currentTimeMillis()));
                return Optional.of(new PaymentIntent(pid, txn, amt, "VND", code, remaining));
            }
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    public record PaymentRecord(
            UUID paymentId,
            UUID orderId,
            UUID userId,
            String txnRef,
            BigDecimal amount,
            String currency,
            String paymentStatus,
            String orderStatus,
            BigDecimal totalAmount,
            Timestamp paidAt
    ) { }

    public Optional<PaymentRecord> findPaymentByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) return Optional.empty();
        try (Connection conn = worker.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT payment_id,order_id,user_id,txn_ref,amount,currency,
                            payment_status,order_status,total_amount,paid_at
                     FROM dbo.vw_PaymentRuntimeDetails WHERE txn_ref=?
                     """)) {
            ps.setString(1, txnRef.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new PaymentRecord(
                        UUID.fromString(rs.getString("payment_id")),
                        UUID.fromString(rs.getString("order_id")),
                        UUID.fromString(rs.getString("user_id")),
                        rs.getString("txn_ref"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("payment_status"),
                        rs.getString("order_status"),
                        rs.getBigDecimal("total_amount"),
                        rs.getTimestamp("paid_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")))
                ));
            }
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    public Optional<vn.ticketscenter.payment.dto.PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId) {
        return getOrderPaymentStatus(orderId, actorId, -1);
    }

    public Optional<vn.ticketscenter.payment.dto.PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId, int authVersion) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        try (Connection conn = buyer.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     """
                     SELECT TOP 1 o.order_id,o.order_status,o.total_amount,
                            p.payment_id,p.payment_status,p.amount as payment_amount,
                            p.currency, p.paid_at
                     FROM dbo.vw_PaymentOrderContext o
                     LEFT JOIN dbo.vw_PaymentRuntimeDetails p ON p.order_id=o.order_id
                     WHERE o.order_id = ? AND o.user_id = ?
                     ORDER BY p.created_at DESC
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UUID oid = UUID.fromString(rs.getString("order_id"));
                String ordStatus = rs.getString("order_status");
                String paymentId = rs.getString("payment_id");
                UUID pid = paymentId == null ? null : UUID.fromString(paymentId);
                String payStatus = rs.getString("payment_status");
                BigDecimal amt = rs.getBigDecimal("payment_amount");
                if (amt == null) amt = rs.getBigDecimal("total_amount");
                String curr = rs.getString("currency");
                Timestamp paidAt = rs.getTimestamp("paid_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
                return Optional.of(new vn.ticketscenter.payment.dto.PaymentStatusResult(
                        oid,
                        ordStatus,
                        Optional.ofNullable(pid),
                        Optional.ofNullable(payStatus),
                        amt != null ? amt : BigDecimal.ZERO,
                        curr != null ? curr.trim() : "VND",
                        Optional.ofNullable(paidAt).map(Timestamp::toInstant)
                ));
            }
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    public void applyPaymentResult(UUID orderId, UUID paymentId, String status, String providerReference) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(status, "status is required");

        String ticketsJson = "[]";
        if ("CAPTURED".equals(status)) {
            ticketsJson = generateTicketsForOrder(orderId, worker);
        }

        org.json.JSONObject verifiedResultJson = new org.json.JSONObject();
        verifiedResultJson.put("status", status);
        verifiedResultJson.put("providerReference", providerReference != null ? providerReference : "VNPAY");
        String verifiedResult = verifiedResultJson.toString();

        try (Connection conn = worker.getConnection();
             CallableStatement cs = conn.prepareCall("{call dbo.usp_ApplyPaymentResult(?, ?, ?, ?, ?)}")) {
            cs.setObject(1, orderId);
            cs.setNull(2, Types.OTHER);
            cs.setObject(3, paymentId);
            cs.setString(4, verifiedResult);
            cs.setString(5, ticketsJson);
            cs.execute();
        } catch (SQLException failure) {
            PaymentException mapped = domain(failure);
            if (mapped != null) throw mapped;
            throw PaymentException.unavailable();
        }
    }

    private String generateTicketsForOrder(UUID orderId, DataSource source) {
        int expected = 0;
        try (Connection conn = source.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT quantity FROM dbo.vw_OrderTicketQuantity WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    expected = rs.getInt(1);
                }
            }
        } catch (SQLException failure) { throw PaymentException.unavailable(); }
        if (expected < 1) throw PaymentException.unavailable();

        org.json.JSONArray tickets = new org.json.JSONArray();
        for (int i = 0; i < expected; i++) {
            String ticketCode = "TC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            byte[] secret = new byte[32];
            new java.security.SecureRandom().nextBytes(secret);
            try {
                java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
                String qrHashHex = java.util.HexFormat.of().formatHex(sha256.digest(secret));
                org.json.JSONObject t = new org.json.JSONObject();
                t.put("ticketCode", ticketCode);
                t.put("qrSecretHashHex", qrHashHex);
                tickets.put(t);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return tickets.toString();
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
