package vn.ticketscenter.payment.dao;

import vn.ticketscenter.config.error.AppException;

import org.json.JSONArray;
import org.json.JSONObject;
import vn.ticketscenter.payment.dto.PaymentStatusResult;
import vn.ticketscenter.payment.service.PaymentException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Payment reads go through views, writes through procedures. The buyer principal opens payments for its own
 * orders; only the worker principal may apply a provider result ({@code usp_ApplyPaymentResult}).
 */
public class PaymentRepository {
    private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DataSource buyer;
    private final DataSource worker;

    public PaymentRepository(DataSource buyer) {
        this(buyer, buyer);
    }

    public PaymentRepository(DataSource buyer, DataSource worker) {
        this.buyer = Objects.requireNonNull(buyer, "buyer DataSource is required");
        this.worker = Objects.requireNonNull(worker, "worker DataSource is required");
    }

    /** A payment opened (or reused) for an order, plus how long its seat hold still lasts. */
    public record PaymentIntent(
            UUID paymentId, String txnRef, BigDecimal amount, Duration holdRemaining) {}

    public record PaymentRecord(
            UUID paymentId, UUID orderId, String txnRef, BigDecimal amount, String paymentStatus) {}

    public record ReconcileCandidate(
            UUID paymentId, UUID orderId, String txnRef, BigDecimal amount, Instant createdAt) {}

    /** The order costs 0 VND: it is completed with {@link #completeZeroOrder} instead of a payment. */
    public static final class ZeroOrderDetectedException extends AppException {
        public ZeroOrderDetectedException() {
            super(409, "ORDER_ZERO_AMOUNT", "Order is 0 VND");
        }
    }

    // ---------------------------------------------------------------- Buyer: start paying

    public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");

        Duration holdRemaining = checkOrderIsPayable(orderId, actorId);
        return withRetry(
                () -> {
                    try (Connection conn = buyer.getConnection();
                            PreparedStatement ps =
                                    conn.prepareStatement("EXEC dbo.usp_BeginOrderPayment ?, ?")) {
                        conn.setAutoCommit(true);
                        ps.setQueryTimeout(20);
                        ps.setString(1, orderId.toString());
                        ps.setString(2, actorId.toString());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw PaymentException.unavailable();
                            return new PaymentIntent(
                                    UUID.fromString(rs.getString("payment_id")),
                                    rs.getString("txn_ref"),
                                    rs.getBigDecimal("amount"),
                                    holdRemaining);
                        }
                    }
                });
    }

    /** @return how long the seat hold still lasts; throws when the order cannot be paid now */
    private Duration checkOrderIsPayable(UUID orderId, UUID actorId) {
        try (Connection conn = buyer.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                """
                     SELECT total_amount, expires_at, order_status, hold_status, event_status
                     FROM dbo.vw_PaymentOrderContext WHERE order_id = ? AND user_id = ?
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    assertOrderAccess(conn, orderId);
                    throw PaymentException.orderNotFound();
                }
                if (!"PENDING_PAYMENT".equals(rs.getString("order_status"))
                        || !"ACTIVE".equals(rs.getString("hold_status"))) {
                    throw PaymentException.orderNotPayable();
                }
                if (!"PUBLISHED".equals(rs.getString("event_status")))
                    throw PaymentException.eventUnavailable();

                long remainingMs =
                        rs.getTimestamp("expires_at", UTC).getTime() - System.currentTimeMillis();
                if (remainingMs <= 0) throw PaymentException.orderNotPayable();
                if (rs.getBigDecimal("total_amount").signum() == 0)
                    throw new ZeroOrderDetectedException();
                return Duration.ofMillis(remainingMs);
            }
        } catch (SQLException failure) {
            throw toPaymentException(failure);
        }
    }

    public void completeZeroOrder(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        String tickets = newTicketIdentities(orderId, buyer);
        try (Connection conn = buyer.getConnection();
                CallableStatement cs =
                        conn.prepareCall("{call dbo.usp_CompleteFreeOrder(?, ?, ?)}")) {
            cs.setObject(1, orderId);
            cs.setObject(2, actorId);
            cs.setString(3, tickets);
            cs.execute();
        } catch (SQLException failure) {
            throw toPaymentException(failure);
        }
    }

    public Optional<PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        try (Connection conn = buyer.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                """
                     SELECT TOP 1 o.order_id, o.order_status, o.total_amount,
                            p.payment_id, p.payment_status, p.amount AS payment_amount, p.currency, p.paid_at
                     FROM dbo.vw_PaymentOrderContext o
                     LEFT JOIN dbo.vw_PaymentRuntimeDetails p ON p.order_id = o.order_id
                     WHERE o.order_id = ? AND o.user_id = ?
                     ORDER BY p.created_at DESC
                     """)) {
            ps.setObject(1, orderId);
            ps.setObject(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    assertOrderAccess(conn, orderId);
                    return Optional.empty();
                }
                BigDecimal amount =
                        Optional.ofNullable(rs.getBigDecimal("payment_amount"))
                                .orElse(
                                        Optional.ofNullable(rs.getBigDecimal("total_amount"))
                                                .orElse(BigDecimal.ZERO));
                String currency = rs.getString("currency");
                return Optional.of(
                        new PaymentStatusResult(
                                UUID.fromString(rs.getString("order_id")),
                                rs.getString("order_status"),
                                Optional.ofNullable(rs.getString("payment_id"))
                                        .map(UUID::fromString),
                                Optional.ofNullable(rs.getString("payment_status")),
                                amount,
                                currency == null ? "VND" : currency.trim(),
                                Optional.ofNullable(rs.getTimestamp("paid_at", UTC))
                                        .map(Timestamp::toInstant)));
            }
        } catch (SQLException failure) {
            throw toPaymentException(failure);
        }
    }

    // ---------------------------------------------------------------- Worker: settle with the
    // payOS result

    public Optional<PaymentRecord> findPaymentByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) return Optional.empty();
        try (Connection conn = worker.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT payment_id, order_id, txn_ref, amount, payment_status FROM dbo.vw_PaymentRuntimeDetails WHERE txn_ref = ?")) {
            ps.setString(1, txnRef.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(
                        new PaymentRecord(
                                UUID.fromString(rs.getString("payment_id")),
                                UUID.fromString(rs.getString("order_id")),
                                rs.getString("txn_ref"),
                                rs.getBigDecimal("amount"),
                                rs.getString("payment_status")));
            }
        } catch (SQLException failure) {
            throw toPaymentException(failure);
        }
    }

    /**
     * payOS reports a numeric order code that maps to a txn_ref prefix (see PayosOrderCodes). The prefix must
     * match exactly one payment; an ambiguous match is treated as unknown rather than guessed.
     */
    public Optional<PaymentRecord> findPaymentByTxnRefPrefix(String txnRefPrefix) {
        if (txnRefPrefix == null || !txnRefPrefix.matches("[A-Za-z0-9-]{1,90}"))
            return Optional.empty();
        Optional<String> txnRef;
        try (Connection conn = worker.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT TOP 2 txn_ref FROM dbo.vw_PaymentRuntimeDetails WHERE txn_ref LIKE ?")) {
            ps.setString(1, txnRefPrefix + "%");
            ps.setQueryTimeout(15);
            try (ResultSet rs = ps.executeQuery()) {
                txnRef = rs.next() ? Optional.of(rs.getString("txn_ref")) : Optional.empty();
                if (rs.next()) txnRef = Optional.empty();
            }
        } catch (SQLException failure) {
            throw PaymentException.unavailable();
        }
        // The worker pool may hold a single connection: look the payment up only after closing the
        // first one.
        return txnRef.flatMap(this::findPaymentByTxnRef);
    }

    public List<ReconcileCandidate> findPaymentsToReconcile(int limit) {
        try (Connection conn = worker.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT TOP (?) id, order_id, txn_ref, amount, created_at FROM dbo.vw_PaymentsToReconcile ORDER BY created_at")) {
            ps.setInt(1, limit);
            ps.setQueryTimeout(20);
            List<ReconcileCandidate> candidates = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidates.add(
                            new ReconcileCandidate(
                                    UUID.fromString(rs.getString("id")),
                                    UUID.fromString(rs.getString("order_id")),
                                    rs.getString("txn_ref"),
                                    rs.getBigDecimal("amount"),
                                    rs.getTimestamp("created_at", UTC).toInstant()));
                }
            }
            return candidates;
        } catch (SQLException failure) {
            throw PaymentException.unavailable();
        }
    }

    /** @param status CAPTURED, FAILED or UNKNOWN; tickets are generated only for CAPTURED */
    public void applyPaymentResult(
            UUID orderId, UUID paymentId, String status, String providerReference) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(status, "status is required");

        String tickets = "CAPTURED".equals(status) ? newTicketIdentities(orderId, worker) : "[]";
        // "0" is a placeholder some gateways send for cancelled transactions, not a provider
        // identity.
        boolean noReference =
                providerReference == null
                        || providerReference.isBlank()
                        || ("0".equals(providerReference) && !"CAPTURED".equals(status));
        String verifiedResult =
                new JSONObject()
                        .put("status", status)
                        .put("providerReference", noReference ? JSONObject.NULL : providerReference)
                        .toString();

        try (Connection conn = worker.getConnection();
                CallableStatement cs =
                        conn.prepareCall("{call dbo.usp_ApplyPaymentResult(?, ?, ?, ?, ?)}")) {
            cs.setObject(1, orderId);
            cs.setNull(2, Types.OTHER);
            cs.setObject(3, paymentId);
            cs.setString(4, verifiedResult);
            cs.setString(5, tickets);
            cs.execute();
        } catch (SQLException failure) {
            throw toPaymentException(failure);
        }
    }

    // ---------------------------------------------------------------- Helpers

    /** One ticket code + SHA-256 of a random QR secret per ticket the order contains. */
    private static String newTicketIdentities(UUID orderId, DataSource source) {
        int quantity = 0;
        try (Connection conn = source.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT quantity FROM dbo.vw_OrderTicketQuantity WHERE order_id = ?")) {
            ps.setObject(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) quantity = rs.getInt(1);
            }
        } catch (SQLException failure) {
            throw PaymentException.unavailable();
        }
        if (quantity < 1) throw PaymentException.unavailable();

        JSONArray tickets = new JSONArray();
        for (int i = 0; i < quantity; i++) {
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            tickets.put(
                    new JSONObject()
                            .put(
                                    "ticketCode",
                                    "TC-"
                                            + UUID.randomUUID()
                                                    .toString()
                                                    .replace("-", "")
                                                    .substring(0, 12)
                                                    .toUpperCase())
                            .put("qrSecretHashHex", HexFormat.of().formatHex(sha256(secret))));
        }
        return tickets.toString();
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** Throws forbidden / not found for an order the caller may not see. */
    private static void assertOrderAccess(Connection conn, UUID orderId) {
        try (CallableStatement cs = conn.prepareCall("{call dbo.usp_AssertOrderAccess(?)}")) {
            cs.setObject(1, orderId);
            cs.execute();
        } catch (SQLException failure) {
            throw switch (failure.getErrorCode()) {
                case 51403 -> PaymentException.forbidden();
                case 51404 -> PaymentException.orderNotFound();
                default -> PaymentException.unavailable();
            };
        }
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T run() throws SQLException;
    }

    /** Retries deadlocks, lock timeouts and dropped connections up to 3 times. */
    private static <T> T withRetry(SqlCall<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.run();
            } catch (SQLException failure) {
                PaymentException mapped = domainError(failure);
                if (mapped != null) throw mapped;
                if (attempt == 3 || !isTransient(failure)) throw PaymentException.unavailable();
            }
        }
    }

    private static PaymentException toPaymentException(SQLException failure) {
        PaymentException mapped = domainError(failure);
        return mapped != null ? mapped : PaymentException.unavailable();
    }

    /** THROW numbers of the payment procedures. */
    private static PaymentException domainError(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            switch (error.getErrorCode()) {
                case 51280, 51395:
                    return PaymentException.orderNotPayable();
                case 51281:
                    return PaymentException.eventUnavailable();
                case 51392:
                    return PaymentException.orderNotFound();
                case 51393:
                    return PaymentException.forbidden();
                case 51232, 51389:
                    return PaymentException.paymentUnresolved();
                default:
                    break;
            }
        }
        return null;
    }

    private static boolean isTransient(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            if (error.getErrorCode() == 1205 || error.getErrorCode() == 1222) return true;
            String state = error.getSQLState();
            if (state != null
                    && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01")))
                return true;
        }
        return false;
    }
}
