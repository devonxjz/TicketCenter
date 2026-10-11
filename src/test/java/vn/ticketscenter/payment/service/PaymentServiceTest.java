package vn.ticketscenter.payment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.dto.PaymentActor;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.repository.PaymentRepository;
import vn.ticketscenter.payment.repository.PaymentRepository.PaymentIntent;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PaymentServiceTest {
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID KEY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final PaymentActor ACTOR = new PaymentActor(ACTOR_ID, 1);

    private MockJedis jedis;
    private RedisPaymentIdempotency idempotency;
    private VnpaySigner signer;

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

    private static class StubPaymentRepository extends PaymentRepository {
        PaymentIntent intentToReturn;
        boolean zeroOrderDetected = false;
        final AtomicBoolean zeroCompleted = new AtomicBoolean(false);

        public StubPaymentRepository() {
            super(new DataSourceStub());
        }

        @Override
        public PaymentIntent beginOrderPayment(UUID orderId, UUID actorId) {
            if (zeroOrderDetected) {
                throw new ZeroOrderDetectedException(orderId);
            }
            if (intentToReturn == null) {
                throw PaymentException.orderNotPayable();
            }
            return intentToReturn;
        }

        @Override
        public void completeZeroOrder(UUID orderId, UUID actorId) {
            zeroCompleted.set(true);
        }
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

    @BeforeEach
    void setUp() {
        jedis = new MockJedis();
        idempotency = new RedisPaymentIdempotency(() -> jedis);
        Clock fixedClock = Clock.fixed(Instant.parse("2026-10-11T12:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        signer = new VnpaySigner("TESTTMN", "TESTSECRET", "https://pay.vnpay.vn", "http://return", fixedClock);
    }

    @Test
    void successfullyInitiatesPaidOrderAndEnablesReplay() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.intentToReturn = new PaymentIntent(pid, "PAY-12345", new BigDecimal("350000"), "VND", "ORD-999", Duration.ofMinutes(10));

        PaymentService service = new PaymentService(repo, signer, idempotency);

        // Lần đầu: Khởi tạo mới
        PaymentInitiationResult result = service.initiate(ACTOR, ORDER_ID, KEY, Optional.of("NCB"), "127.0.0.1");
        assertFalse(result.replayed());
        assertFalse(result.zeroOrder());
        assertEquals(200, result.httpStatus());
        assertEquals(Optional.of(pid), result.paymentId());
        assertEquals(Optional.of("PAY-12345"), result.txnRef());
        assertTrue(result.redirectUrl().isPresent());
        assertTrue(result.redirectUrl().get().contains("vnp_SecureHash"));

        // Lần 2: Cùng key & cùng payload ➔ Replay thành công
        PaymentInitiationResult replayResult = service.initiate(ACTOR, ORDER_ID, KEY, Optional.of("NCB"), "127.0.0.1");
        assertTrue(replayResult.replayed());
        assertEquals(result.redirectUrl(), replayResult.redirectUrl());
        assertEquals(result.paymentId(), replayResult.paymentId());
    }

    @Test
    void detectsIdempotencyConflictOnChangedPayload() {
        StubPaymentRepository repo = new StubPaymentRepository();
        repo.intentToReturn = new PaymentIntent(UUID.randomUUID(), "PAY-1", new BigDecimal("100000"), "VND", "ORD-1", Duration.ofMinutes(10));

        PaymentService service = new PaymentService(repo, signer, idempotency);

        service.initiate(ACTOR, ORDER_ID, KEY, Optional.of("NCB"), "127.0.0.1");

        // Cùng KEY nhưng khác bankCode ➔ 409 IDEMPOTENCY_CONFLICT
        PaymentException ex = assertThrows(PaymentException.class, () ->
                service.initiate(ACTOR, ORDER_ID, KEY, Optional.of("VNPAYQR"), "127.0.0.1"));
        assertEquals(409, ex.status());
        assertEquals("IDEMPOTENCY_CONFLICT", ex.code());
    }

    @Test
    void handlesZeroOrderWithoutCallingVnpay() {
        StubPaymentRepository repo = new StubPaymentRepository();
        repo.zeroOrderDetected = true;

        PaymentService service = new PaymentService(repo, signer, idempotency);

        PaymentInitiationResult result = service.initiate(ACTOR, ORDER_ID, KEY, Optional.empty(), "127.0.0.1");
        assertTrue(result.zeroOrder());
        assertFalse(result.replayed());
        assertEquals(200, result.httpStatus());
        assertEquals(BigDecimal.ZERO, result.amount());
        assertTrue(result.redirectUrl().isEmpty());
        assertTrue(repo.zeroCompleted.get());

        // Replay zero-order
        PaymentInitiationResult replayed = service.initiate(ACTOR, ORDER_ID, KEY, Optional.empty(), "127.0.0.1");
        assertTrue(replayed.zeroOrder());
        assertTrue(replayed.replayed());
    }

    @Test
    void concurrentOrderModificationIsBlocked() throws Exception {
        StubPaymentRepository repo = new StubPaymentRepository();
        repo.intentToReturn = new PaymentIntent(UUID.randomUUID(), "PAY-1", new BigDecimal("100000"), "VND", "ORD-1", Duration.ofMinutes(10));

        PaymentService service = new PaymentService(repo, signer, idempotency);

        // Giả lập khóa order đang bị giữ bởi thread khác
        try (AutoCloseable heldLock = idempotency.acquireOrderLock(ORDER_ID)) {
            assertNotNull(heldLock);
            PaymentException ex = assertThrows(PaymentException.class, () ->
                    service.initiate(ACTOR, ORDER_ID, KEY, Optional.empty(), "127.0.0.1"));
            assertEquals(409, ex.status());
            assertEquals("CONCURRENT_REQUEST", ex.code());
        }
    }

    @Test
    void cancelsClaimWhenRepositoryThrows() {
        StubPaymentRepository repo = new StubPaymentRepository();
        repo.intentToReturn = null; // Sẽ throw orderNotPayable()

        PaymentService service = new PaymentService(repo, signer, idempotency);

        assertThrows(PaymentException.class, () ->
                service.initiate(ACTOR, ORDER_ID, KEY, Optional.empty(), "127.0.0.1"));

        // Sau khi bị lỗi, claim đã được hủy, key không bị treo ở pending
        assertNull(jedis.get("tc:idem:vnpay:init:" + ACTOR_ID + ":" + KEY));
    }
}
