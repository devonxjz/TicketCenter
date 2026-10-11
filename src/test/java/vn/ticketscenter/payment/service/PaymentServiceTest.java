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
        PaymentRecord recordToReturn;
        vn.ticketscenter.payment.dto.PaymentStatusResult statusToReturn;
        String appliedStatus;
        String appliedReference;
        boolean zeroOrderDetected = false;
        final AtomicBoolean zeroCompleted = new AtomicBoolean(false);

        public StubPaymentRepository() {
            super(new DataSourceStub());
        }

        @Override
        public Optional<PaymentRecord> findPaymentByTxnRef(String txnRef) {
            return Optional.ofNullable(recordToReturn);
        }

        @Override
        public Optional<vn.ticketscenter.payment.dto.PaymentStatusResult> getOrderPaymentStatus(UUID orderId, UUID actorId) {
            return Optional.ofNullable(statusToReturn);
        }

        @Override
        public void applyPaymentResult(UUID orderId, UUID paymentId, String status, String providerReference) {
            this.appliedStatus = status;
            this.appliedReference = providerReference;
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

    @Test
    void processIpnReturns97OnInvalidChecksum() {
        StubPaymentRepository repo = new StubPaymentRepository();
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "TXN-1");
        params.put("vnp_SecureHash", "INVALID_HASH");

        var result = service.processIpn(params);
        assertEquals("97", result.rspCode());
        assertEquals("Invalid Checksum", result.message());
    }

    @Test
    void processIpnReturns01WhenOrderNotFound() {
        StubPaymentRepository repo = new StubPaymentRepository();
        repo.recordToReturn = null;
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_TxnRef", "TXN-NOT-FOUND");
        String hash = VnpaySigner.hmacSha512("TESTSECRET", "vnp_Amount=10000000&vnp_TxnRef=TXN-NOT-FOUND");
        params.put("vnp_SecureHash", hash);

        var result = service.processIpn(params);
        assertEquals("01", result.rspCode());
        assertEquals("Order Not Found", result.message());
    }

    @Test
    void processIpnReturns04WhenAmountMismatch() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.recordToReturn = new PaymentRepository.PaymentRecord(
                pid, ORDER_ID, ACTOR_ID, "TXN-1", new BigDecimal("100000"), "VND",
                "PENDING", "PENDING_PAYMENT", new BigDecimal("100000"), null
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "20000000"); // 200,000 VND instead of 100,000 VND
        params.put("vnp_TxnRef", "TXN-1");
        String hash = VnpaySigner.hmacSha512("TESTSECRET", "vnp_Amount=20000000&vnp_TxnRef=TXN-1");
        params.put("vnp_SecureHash", hash);

        var result = service.processIpn(params);
        assertEquals("04", result.rspCode());
        assertEquals("Invalid Amount", result.message());
    }

    @Test
    void processIpnReturns02WhenAlreadyConfirmed() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.recordToReturn = new PaymentRepository.PaymentRecord(
                pid, ORDER_ID, ACTOR_ID, "TXN-1", new BigDecimal("100000"), "VND",
                "CAPTURED", "PAID", new BigDecimal("100000"), new java.sql.Timestamp(System.currentTimeMillis())
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_TxnRef", "TXN-1");
        String hash = VnpaySigner.hmacSha512("TESTSECRET", "vnp_Amount=10000000&vnp_TxnRef=TXN-1");
        params.put("vnp_SecureHash", hash);

        var result = service.processIpn(params);
        assertEquals("02", result.rspCode());
        assertEquals("Order already confirmed", result.message());
    }

    @Test
    void processIpnSuccessfullyMarksCapturedAndCachesResult() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.recordToReturn = new PaymentRepository.PaymentRecord(
                pid, ORDER_ID, ACTOR_ID, "TXN-1", new BigDecimal("100000"), "VND",
                "PENDING", "PENDING_PAYMENT", new BigDecimal("100000"), null
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionNo", "14567890");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", "TXN-1");

        String raw = "vnp_Amount=10000000&vnp_ResponseCode=00&vnp_TransactionNo=14567890&vnp_TransactionStatus=00&vnp_TxnRef=TXN-1";
        String hash = VnpaySigner.hmacSha512("TESTSECRET", raw);
        params.put("vnp_SecureHash", hash);

        var result = service.processIpn(params);
        assertEquals("00", result.rspCode());
        assertEquals("Confirm Success", result.message());
        assertEquals("CAPTURED", repo.appliedStatus);
        assertEquals("14567890", repo.appliedReference);

        // Lần thứ 2 gọi lại cùng txnRef & transactionNo -> Đọc từ cache trả 02
        var replay = service.processIpn(params);
        assertEquals("02", replay.rspCode());
    }

    @Test
    void processIpnMarksFailedWhenResponseCodeNot00() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.recordToReturn = new PaymentRepository.PaymentRecord(
                pid, ORDER_ID, ACTOR_ID, "TXN-1", new BigDecimal("100000"), "VND",
                "PENDING", "PENDING_PAYMENT", new BigDecimal("100000"), null
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "10000000");
        params.put("vnp_ResponseCode", "24"); // Khách hàng hủy giao dịch
        params.put("vnp_TransactionNo", "14567891");
        params.put("vnp_TxnRef", "TXN-1");

        String raw = "vnp_Amount=10000000&vnp_ResponseCode=24&vnp_TransactionNo=14567891&vnp_TxnRef=TXN-1";
        String hash = VnpaySigner.hmacSha512("TESTSECRET", raw);
        params.put("vnp_SecureHash", hash);

        var result = service.processIpn(params);
        assertEquals("00", result.rspCode());
        assertEquals("FAILED", repo.appliedStatus);
    }

    @Test
    void processReturnHandlesValidAndInvalidSignatures() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.recordToReturn = new PaymentRepository.PaymentRecord(
                pid, ORDER_ID, ACTOR_ID, "TXN-1", new BigDecimal("100000"), "VND",
                "PENDING", "PENDING_PAYMENT", new BigDecimal("100000"), null
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        Map<String, String> validParams = new HashMap<>();
        validParams.put("vnp_Amount", "10000000");
        validParams.put("vnp_ResponseCode", "00");
        validParams.put("vnp_TxnRef", "TXN-1");
        String hash = VnpaySigner.hmacSha512("TESTSECRET", "vnp_Amount=10000000&vnp_ResponseCode=00&vnp_TxnRef=TXN-1");
        validParams.put("vnp_SecureHash", hash);

        var validReturn = service.processReturn(validParams);
        assertTrue(validReturn.validSignature());
        assertTrue(validReturn.success());
        assertEquals(0, new BigDecimal("100000").compareTo(validReturn.amount()));

        // Sai hash
        Map<String, String> invalidParams = new HashMap<>(validParams);
        invalidParams.put("vnp_SecureHash", "TAMPERED");
        var invalidReturn = service.processReturn(invalidParams);
        assertFalse(invalidReturn.validSignature());
        assertFalse(invalidReturn.success());
    }

    @Test
    void getOrderPaymentStatusReturnsCorrectDetails() {
        StubPaymentRepository repo = new StubPaymentRepository();
        UUID pid = UUID.randomUUID();
        repo.statusToReturn = new vn.ticketscenter.payment.dto.PaymentStatusResult(
                ORDER_ID, "PAID", Optional.of(pid), Optional.of("CAPTURED"),
                new BigDecimal("150000"), "VND", Optional.of(Instant.now())
        );
        PaymentService service = new PaymentService(repo, signer, idempotency);

        var status = service.getOrderPaymentStatus(ACTOR_ID, ORDER_ID);
        assertEquals(ORDER_ID, status.orderId());
        assertEquals("PAID", status.orderStatus());
        assertEquals(Optional.of("CAPTURED"), status.paymentStatus());
    }
}
