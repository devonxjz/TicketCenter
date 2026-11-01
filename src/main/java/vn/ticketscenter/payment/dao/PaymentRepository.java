package vn.ticketscenter.payment.dao;

import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.persistence.SqlCall;

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

import static vn.ticketscenter.config.persistence.SqlCall.instant;
import static vn.ticketscenter.config.persistence.SqlCall.uuid;

/**
 * Payment reads go through views, writes through procedures. The buyer principal opens payments for its own
 * orders; only the worker principal may apply a provider result ({@code usp_ApplyPaymentResult}).
 */
public class PaymentRepository {
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

    /** THROW numbers of the payment procedures; every other database failure means "try again later". */
    private static final SqlCall.ErrorTable ERRORS =
            SqlCall.ErrorTable.of()
                    .on(new int[] {51280, 51395}, m -> PaymentException.orderNotPayable())
                    .on(51281, m -> PaymentException.eventUnavailable())
                    .on(51392, m -> PaymentException.orderNotFound())
                    .on(51393, m -> PaymentException.forbidden())
                    .on(new int[] {51232, 51389}, m -> PaymentException.paymentUnresolved())
                    .fallback((code, message) -> PaymentException.unavailable());

    private static final SqlCall.ErrorTable UNAVAILABLE =
            SqlCall.ErrorTable.of().fallback((code, message) -> PaymentException.unavailable());

    private static final SqlCall.ErrorTable ACCESS_ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51403, m -> PaymentException.forbidden())
                    .on(51404, m -> PaymentException.orderNotFound())
                    .fallback((code, message) -> PaymentException.unavailable());

    // ---------------------------------------------------------------- Buyer: start paying

    public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        Duration holdRemaining = checkOrderIsPayable(orderId, actorId);
        return SqlCall.callWithRetry(
                buyer,
                "EXEC dbo.usp_BeginOrderPayment ?, ?",
                rs ->
                        new PaymentIntent(
                                UUID.fromString(rs.getString("payment_id")),
                                rs.getString("txn_ref"),
                                rs.getBigDecimal("amount"),
                                holdRemaining),
                ERRORS,
                PaymentException::unavailable,
                orderId,
                actorId);
    }

    private record PayableOrder(
            BigDecimal total, Instant expiresAt, String orderStatus, String holdStatus, String eventStatus) {}

    /** @return how long the seat hold still lasts; throws when the order cannot be paid now */
    private Duration checkOrderIsPayable(UUID orderId, UUID actorId) {
        PayableOrder order =
                SqlCall.onConnection(
                        buyer,
                        conn -> {
                            Optional<PaymentRepository.PayableOrder> row =
                                    SqlCall.queryOptional(
                                            conn,
                                            """
                                            SELECT total_amount, expires_at, order_status, hold_status, event_status
                                            FROM dbo.vw_PaymentOrderContext WHERE order_id = ? AND user_id = ?
                                            """,
                                            rs ->
                                                    new PayableOrder(
                                                            rs.getBigDecimal("total_amount"),
                                                            instant(rs, "expires_at"),
                                                            rs.getString("order_status"),
                                                            rs.getString("hold_status"),
                                                            rs.getString("event_status")),
                                            ERRORS,
                                            orderId,
                                            actorId);
                            if (row.isEmpty()) {
                                assertOrderAccess(conn, orderId);
                                throw PaymentException.orderNotFound();
                            }
                            return row.get();
                        },
                        ERRORS);
        if (!"PENDING_PAYMENT".equals(order.orderStatus()) || !"ACTIVE".equals(order.holdStatus())) {
            throw PaymentException.orderNotPayable();
        }
        if (!"PUBLISHED".equals(order.eventStatus())) {
            throw PaymentException.eventUnavailable();
        }
        long remainingMs = order.expiresAt().toEpochMilli() - System.currentTimeMillis();
        if (remainingMs <= 0) {
            throw PaymentException.orderNotPayable();
        }
        if (order.total().signum() == 0) {
            throw new ZeroOrderDetectedException();
        }
        return Duration.ofMillis(remainingMs);
    }

    public void completeZeroOrder(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        String tickets = newTicketIdentities(orderId, buyer);
        SqlCall.onConnection(
                buyer,
                conn -> {
                    SqlCall.execute(
                            conn, "{call dbo.usp_CompleteFreeOrder(?, ?, ?)}", ERRORS, orderId, actorId, tickets);
                    return null;
                },
                ERRORS);
    }

    public Optional<PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(actorId, "actorId is required");
        return SqlCall.onConnection(
                buyer,
                conn -> {
                    Optional<PaymentStatusResult> status =
                            SqlCall.queryOptional(
                                    conn,
                                    """
                                    SELECT TOP 1 o.order_id, o.order_status, o.total_amount,
                                           p.payment_id, p.payment_status, p.amount AS payment_amount, p.currency, p.paid_at
                                    FROM dbo.vw_PaymentOrderContext o
                                    LEFT JOIN dbo.vw_PaymentRuntimeDetails p ON p.order_id = o.order_id
                                    WHERE o.order_id = ? AND o.user_id = ?
                                    ORDER BY p.created_at DESC
                                    """,
                                    PaymentRepository::paymentStatus,
                                    ERRORS,
                                    orderId,
                                    actorId);
                    if (status.isEmpty()) {
                        assertOrderAccess(conn, orderId);
                    }
                    return status;
                },
                ERRORS);
    }

    private static PaymentStatusResult paymentStatus(ResultSet rs) throws SQLException {
        BigDecimal amount = rs.getBigDecimal("payment_amount");
        if (amount == null) {
            amount = rs.getBigDecimal("total_amount");
        }
        String currency = rs.getString("currency");
        return new PaymentStatusResult(
                UUID.fromString(rs.getString("order_id")),
                rs.getString("order_status"),
                Optional.ofNullable(uuid(rs, "payment_id")),
                Optional.ofNullable(rs.getString("payment_status")),
                amount == null ? BigDecimal.ZERO : amount,
                currency == null ? "VND" : currency.trim(),
                Optional.ofNullable(instant(rs, "paid_at")));
    }

    // ---------------------------------------------------------------- Worker: settle with the payOS result

    public Optional<PaymentRecord> findPaymentByTxnRef(String txnRef) {
        if (txnRef == null || txnRef.isBlank()) {
            return Optional.empty();
        }
        return SqlCall.onConnection(
                worker,
                conn ->
                        SqlCall.queryOptional(
                                conn,
                                "SELECT payment_id, order_id, txn_ref, amount, payment_status FROM dbo.vw_PaymentRuntimeDetails WHERE txn_ref = ?",
                                rs ->
                                        new PaymentRecord(
                                                UUID.fromString(rs.getString("payment_id")),
                                                UUID.fromString(rs.getString("order_id")),
                                                rs.getString("txn_ref"),
                                                rs.getBigDecimal("amount"),
                                                rs.getString("payment_status")),
                                ERRORS,
                                txnRef.trim()),
                ERRORS);
    }

    /**
     * payOS reports a numeric order code that maps to a txn_ref prefix (see PayosOrderCodes). The prefix must
     * match exactly one payment; an ambiguous match is treated as unknown rather than guessed.
     */
    public Optional<PaymentRecord> findPaymentByTxnRefPrefix(String txnRefPrefix) {
        if (txnRefPrefix == null || !txnRefPrefix.matches("[A-Za-z0-9-]{1,90}")) {
            return Optional.empty();
        }
        List<String> matches =
                SqlCall.onConnection(
                        worker,
                        conn ->
                                SqlCall.call(
                                        conn,
                                        "SELECT TOP 2 txn_ref FROM dbo.vw_PaymentRuntimeDetails WHERE txn_ref LIKE ?",
                                        rows -> {
                                            List<String> refs = new ArrayList<>();
                                            do refs.add(rows.getString("txn_ref"));
                                            while (rows.next());
                                            return refs;
                                        },
                                        UNAVAILABLE,
                                        txnRefPrefix + "%"),
                        UNAVAILABLE);
        // The worker pool may hold a single connection: look the payment up only after closing the first one.
        return matches != null && matches.size() == 1
                ? findPaymentByTxnRef(matches.getFirst())
                : Optional.empty();
    }

    public List<ReconcileCandidate> findPaymentsToReconcile(int limit) {
        return SqlCall.onConnection(
                worker,
                conn ->
                        SqlCall.queryList(
                                conn,
                                "SELECT TOP (?) id, order_id, txn_ref, amount, created_at FROM dbo.vw_PaymentsToReconcile ORDER BY created_at",
                                rs ->
                                        new ReconcileCandidate(
                                                UUID.fromString(rs.getString("id")),
                                                UUID.fromString(rs.getString("order_id")),
                                                rs.getString("txn_ref"),
                                                rs.getBigDecimal("amount"),
                                                instant(rs, "created_at")),
                                UNAVAILABLE,
                                limit),
                UNAVAILABLE);
    }

    /** @param status CAPTURED, FAILED or UNKNOWN; tickets are generated only for CAPTURED */
    public void applyPaymentResult(
            UUID orderId, UUID paymentId, String status, String providerReference) {
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(status, "status is required");

        String tickets = "CAPTURED".equals(status) ? newTicketIdentities(orderId, worker) : "[]";
        // "0" is a placeholder some gateways send for cancelled transactions, not a provider identity.
        boolean noReference =
                providerReference == null
                        || providerReference.isBlank()
                        || ("0".equals(providerReference) && !"CAPTURED".equals(status));
        String verifiedResult =
                new JSONObject()
                        .put("status", status)
                        .put("providerReference", noReference ? JSONObject.NULL : providerReference)
                        .toString();
        SqlCall.onConnection(
                worker,
                conn -> {
                    SqlCall.execute(
                            conn,
                            "{call dbo.usp_ApplyPaymentResult(?, ?, ?, ?, ?)}",
                            ERRORS,
                            orderId,
                            null,
                            paymentId,
                            verifiedResult,
                            tickets);
                    return null;
                },
                ERRORS);
    }

    // ---------------------------------------------------------------- Helpers

    /** One ticket code + SHA-256 of a random QR secret per ticket the order contains. */
    private static String newTicketIdentities(UUID orderId, DataSource source) {
        Integer quantity =
                SqlCall.onConnection(
                        source,
                        conn ->
                                SqlCall.call(
                                        conn,
                                        "SELECT quantity FROM dbo.vw_OrderTicketQuantity WHERE order_id = ?",
                                        rows -> rows.getInt(1),
                                        UNAVAILABLE,
                                        orderId),
                        UNAVAILABLE);
        if (quantity == null || quantity < 1) {
            throw PaymentException.unavailable();
        }

        JSONArray tickets = new JSONArray();
        for (int i = 0; i < quantity; i++) {
            byte[] secret = new byte[32];
            RANDOM.nextBytes(secret);
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            tickets.put(
                    new JSONObject()
                            .put("ticketCode", "TC-" + code)
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
        SqlCall.execute(conn, "{call dbo.usp_AssertOrderAccess(?)}", ACCESS_ERRORS, orderId);
    }
}
