package vn.ticketscenter.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.dto.PaymentReturnResult;
import vn.ticketscenter.payment.dto.PayosWebhookResult;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.payment.integration.PayosOrderCodes;
import vn.ticketscenter.payment.dao.PaymentRepository;
import vn.ticketscenter.payment.dao.PaymentRepository.PaymentIntent;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID KEY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID PAYMENT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String TXN_REF = "PAY-0123456789ABCDEF0123456789ABCDEF";
    private static final long ORDER_CODE = PayosOrderCodes.fromTxnRef(TXN_REF);
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private MockJedis jedis;
    private RedisPaymentIdempotency idempotency;
    private FakePayosGateway gateway;
    private StubPaymentRepository repo;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        jedis = new MockJedis();
        idempotency = new RedisPaymentIdempotency(() -> jedis);
        gateway = new FakePayosGateway();
        repo = new StubPaymentRepository();
        service = new PaymentService(repo, gateway, idempotency, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---------------------------------------------------------------- 1. Initiate

    @Test
    void initiatesPaidOrderWithPayosLinkAndReplaysSameKey() {
        repo.intentToReturn = intent(new BigDecimal("350000"));

        PaymentInitiationResult result = service.initiate(ACTOR_ID, ORDER_ID, KEY);
        assertFalse(result.replayed());
        assertFalse(result.zeroOrder());
        org.json.JSONObject body = new org.json.JSONObject(result.body());
        assertEquals(PAYMENT_ID.toString(), body.getString("paymentId"));
        assertEquals(TXN_REF, body.getString("txnRef"));
        assertEquals("350000", body.getString("amount"));
        assertEquals(Optional.of("https://pay.payos.vn/web/link-" + ORDER_CODE), result.redirectUrl());
        assertEquals(ORDER_CODE, gateway.createdOrderCode);
        assertEquals(350000L, gateway.createdAmount);
        assertEquals("TC" + ORDER_CODE, gateway.createdDescription);
        assertEquals(NOW.plus(Duration.ofMinutes(10)), gateway.createdExpiresAt, "link expires with the seat hold");

        PaymentInitiationResult replay = service.initiate(ACTOR_ID, ORDER_ID, KEY);
        assertTrue(replay.replayed());
        assertEquals(result.redirectUrl(), replay.redirectUrl());
        assertEquals(1, gateway.createCalls, "a replay never calls payOS again");
    }

    @Test
    void sameKeyForAnotherOrderIsAConflict() {
        repo.intentToReturn = intent(new BigDecimal("100000"));
        service.initiate(ACTOR_ID, ORDER_ID, KEY);

        PaymentException ex = assertThrows(PaymentException.class, () ->
                service.initiate(ACTOR_ID, UUID.randomUUID(), KEY));
        assertEquals(409, ex.status());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.code());
    }

    @Test
    void payosFailureNeverCachesARedirect() {
        repo.intentToReturn = intent(new BigDecimal("100000"));
        gateway.createFails = true;

        PaymentException ex = assertThrows(PaymentException.class, () -> service.initiate(ACTOR_ID, ORDER_ID, KEY));
        assertEquals("SERVICE_UNAVAILABLE", ex.code());
        assertNull(jedis.get("tc:idem:payos:init:" + ACTOR_ID + ":" + KEY), "claim is released so the buyer can retry");
    }

    @Test
    void zeroOrderCompletesWithoutCallingPayos() {
        repo.zeroOrderDetected = true;

        PaymentInitiationResult result = service.initiate(ACTOR_ID, ORDER_ID, KEY);
        assertTrue(result.zeroOrder());
        assertEquals("0", new org.json.JSONObject(result.body()).getString("amount"));
        assertTrue(result.redirectUrl().isEmpty());
        assertTrue(repo.zeroCompleted.get());
        assertEquals(0, gateway.createCalls);

        PaymentInitiationResult replayed = service.initiate(ACTOR_ID, ORDER_ID, KEY);
        assertTrue(replayed.zeroOrder());
        assertTrue(replayed.replayed());
    }

    @Test
    void concurrentOrderModificationIsBlocked() throws Exception {
        repo.intentToReturn = intent(new BigDecimal("100000"));
        try (AutoCloseable heldLock = idempotency.acquireOrderLock(ORDER_ID)) {
            assertNotNull(heldLock);
            PaymentException ex = assertThrows(PaymentException.class, () ->
                    service.initiate(ACTOR_ID, ORDER_ID, KEY));
            assertEquals(409, ex.status());
            assertEquals("CONCURRENT_REQUEST", ex.code());
        }
    }

    @Test
    void cancelsClaimWhenRepositoryThrows() {
        repo.intentToReturn = null; // beginOrderPayment throws orderNotPayable()
        assertThrows(PaymentException.class, () -> service.initiate(ACTOR_ID, ORDER_ID, KEY));
        assertNull(jedis.get("tc:idem:payos:init:" + ACTOR_ID + ":" + KEY));
    }

    // ---------------------------------------------------------------- 2. Webhook

    @Test
    void webhookWithInvalidSignatureIsRejected() {
        gateway.webhookInvalid = true;
        PayosWebhookResult result = service.processWebhook("{}");
        assertEquals(400, result.httpStatus());
        assertNull(repo.appliedStatus);
    }

    @Test
    void webhookForUnknownOrderCodeStillAnswers200() {
        gateway.webhook = new PayosGateway.WebhookEvent(123, 3000, "00", "REF", "LINK");
        PayosWebhookResult result = service.processWebhook("{}");
        assertEquals(200, result.httpStatus(), "payOS confirm-webhook sends orderCode 123");
        assertEquals(1, result.error());
    }

    @Test
    void webhookWithWrongAmountIsNotApplied() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.webhook = new PayosGateway.WebhookEvent(ORDER_CODE, 99999, "00", "REF", "LINK");
        assertEquals(4, service.processWebhook("{}").error());
        assertNull(repo.appliedStatus);
    }

    @Test
    void successfulWebhookCapturesOnceAndCachesTheResult() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.webhook = new PayosGateway.WebhookEvent(ORDER_CODE, 100000, "00", "FT123", "LINK-1");

        PayosWebhookResult first = service.processWebhook("{}");
        assertEquals(200, first.httpStatus());
        assertEquals(0, first.error());
        assertEquals("CAPTURED", repo.appliedStatus);
        assertEquals("LINK-1", repo.appliedReference);
        assertEquals(TXN_REF.substring(0, 17), repo.lookedUpPrefix);

        repo.appliedStatus = null;
        assertEquals(PayosWebhookResult.alreadyConfirmed(), service.processWebhook("{}"));
        assertNull(repo.appliedStatus, "a redelivered webhook is answered from cache");
    }

    @Test
    void nonSuccessWebhookIsIgnored() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.webhook = new PayosGateway.WebhookEvent(ORDER_CODE, 100000, "01", "REF", "LINK");
        assertEquals(PayosWebhookResult.ignored(), service.processWebhook("{}"));
        assertNull(repo.appliedStatus);
    }

    // ---------------------------------------------------------------- 3. Return / cancel

    @Test
    void returnWithoutValidOrderCodeIsInvalid() {
        PaymentReturnResult result = service.processReturn(Map.of("orderCode", "abc"));
        assertFalse(result.verified());
        assertFalse(result.success());
    }

    @Test
    void returnTrustsPayosNotTheQueryString() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.link = new PayosGateway.LinkStatus(ORDER_CODE, "LINK-1", "PENDING", 100000, 0);

        PaymentReturnResult result = service.processReturn(Map.of("orderCode", String.valueOf(ORDER_CODE), "status", "PAID", "code", "00"));
        assertFalse(result.success(), "status=PAID in the URL is not proof of payment");
        assertNull(repo.appliedStatus);
    }

    @Test
    void returnAfterPaymentCapturesAndShowsSuccess() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.link = new PayosGateway.LinkStatus(ORDER_CODE, "LINK-1", "PAID", 100000, 100000);

        PaymentReturnResult result = service.processReturn(Map.of("orderCode", String.valueOf(ORDER_CODE)));
        assertTrue(result.verified());
        assertTrue(result.success());
        assertEquals(Optional.of(ORDER_ID), result.orderId());
        assertEquals("CAPTURED", repo.appliedStatus);
        assertEquals("LINK-1", repo.appliedReference);
    }

    @Test
    void cancelledLinkMarksPaymentFailed() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        gateway.link = new PayosGateway.LinkStatus(ORDER_CODE, "LINK-1", "CANCELLED", 100000, 0);

        PaymentReturnResult result = service.processReturn(Map.of("orderCode", String.valueOf(ORDER_CODE), "cancel", "true"));
        assertTrue(result.verified());
        assertFalse(result.success());
        assertEquals("FAILED", repo.appliedStatus);
    }

    @Test
    void capturedButUnsettledReturnStillShowsSuccess() {
        repo.recordToReturn = record("PENDING", new BigDecimal("100000"));
        repo.applyFails = true;
        gateway.link = new PayosGateway.LinkStatus(ORDER_CODE, "LINK-1", "PAID", 100000, 100000);

        PaymentReturnResult result = service.processReturn(Map.of("orderCode", String.valueOf(ORDER_CODE)));
        assertTrue(result.success());
        assertTrue(result.message().contains("đang xác nhận"));
    }

    // ---------------------------------------------------------------- 4. Status

    @Test
    void getOrderPaymentStatusReturnsRepositoryDetails() {
        repo.statusToReturn = new vn.ticketscenter.payment.dto.PaymentStatusResult(
                ORDER_ID, "PAID", Optional.of(PAYMENT_ID), Optional.of("CAPTURED"),
                new BigDecimal("150000"), "VND", Optional.of(Instant.now()));

        var status = service.getOrderPaymentStatus(ACTOR_ID, ORDER_ID);
        assertEquals("PAID", status.orderStatus());
        assertEquals(Optional.of("CAPTURED"), status.paymentStatus());
    }

    // ---------------------------------------------------------------- Test doubles

    private static PaymentIntent intent(BigDecimal amount) {
        return new PaymentIntent(PAYMENT_ID, TXN_REF, amount, Duration.ofMinutes(10));
    }

    private static PaymentRepository.PaymentRecord record(String status, BigDecimal amount) {
        return new PaymentRepository.PaymentRecord(PAYMENT_ID, ORDER_ID, TXN_REF, amount, status);
    }

    private static final class FakePayosGateway implements PayosGateway {
        int createCalls;
        long createdOrderCode, createdAmount;
        String createdDescription;
        Instant createdExpiresAt;
        boolean createFails, webhookInvalid;
        WebhookEvent webhook;
        LinkStatus link;

        @Override public CheckoutLink createLink(long orderCode, long amount, String description, Instant expiresAt) {
            createCalls++;
            if (createFails) throw new IllegalStateException("payOS down");
            createdOrderCode = orderCode; createdAmount = amount; createdDescription = description; createdExpiresAt = expiresAt;
            return new CheckoutLink(orderCode, "link-" + orderCode, "https://pay.payos.vn/web/link-" + orderCode);
        }
        @Override public Optional<LinkStatus> findLink(long orderCode) { return Optional.ofNullable(link); }
        @Override public WebhookEvent verifyWebhook(String body) {
            if (webhookInvalid) throw new InvalidWebhook("bad", null);
            return webhook;
        }
    }

    private static class StubPaymentRepository extends PaymentRepository {
        PaymentIntent intentToReturn;
        PaymentRecord recordToReturn;
        vn.ticketscenter.payment.dto.PaymentStatusResult statusToReturn;
        String appliedStatus, appliedReference, lookedUpPrefix;
        boolean zeroOrderDetected, applyFails;
        final AtomicBoolean zeroCompleted = new AtomicBoolean(false);

        StubPaymentRepository() { super(new DataSourceStub()); }

        @Override public Optional<PaymentRecord> findPaymentByTxnRefPrefix(String prefix) {
            lookedUpPrefix = prefix;
            return Optional.ofNullable(recordToReturn).filter(r -> r.txnRef().startsWith(prefix));
        }
        @Override public Optional<vn.ticketscenter.payment.dto.PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId) {
            return Optional.ofNullable(statusToReturn);
        }
        @Override public void applyPaymentResult(UUID orderId, UUID paymentId, String status, String providerReference) {
            if (applyFails) throw PaymentException.unavailable();
            appliedStatus = status;
            appliedReference = providerReference;
        }
        @Override public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId) {
            if (zeroOrderDetected) throw new ZeroOrderDetectedException();
            if (intentToReturn == null) throw PaymentException.orderNotPayable();
            return intentToReturn;
        }
        @Override public void completeZeroOrder(UUID orderId, UUID actorId) { zeroCompleted.set(true); }
    }

    private static class MockJedis extends Jedis {
        final Map<String, String> store = new HashMap<>();

        @Override public String set(String key, String value, SetParams params) {
            if (store.containsKey(key)) return null;
            store.put(key, value);
            return "OK";
        }
        @Override public String get(String key) { return store.get(key); }
        @Override public long del(String key) { return store.remove(key) != null ? 1 : 0; }
        @Override public Object eval(String script, int keyCount, String... params) {
            String key = params[0], expected = params[1], newVal = params[2];
            if (Objects.equals(store.get(key), expected)) {
                store.put(key, newVal);
                return 1L;
            }
            return 0L;
        }
        @Override public void close() { }
    }

    private static class DataSourceStub implements DataSource {
        @Override public java.sql.Connection getConnection() { throw new UnsupportedOperationException(); }
        @Override public java.sql.Connection getConnection(String u, String p) { throw new UnsupportedOperationException(); }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) { }
        @Override public void setLoginTimeout(int seconds) { }
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return null; }
        @Override public <T> T unwrap(Class<T> iface) { return null; }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
    }
}
