package vn.ticketscenter.payment.service;

import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.dto.PaymentReturnResult;
import vn.ticketscenter.payment.dto.PaymentStatusResult;
import vn.ticketscenter.payment.dto.PayosWebhookResult;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.payment.integration.PayosGateway.Outcome;
import vn.ticketscenter.payment.integration.PayosOrderCodes;
import vn.ticketscenter.payment.dao.PaymentRepository;
import vn.ticketscenter.payment.dao.PaymentRepository.PaymentIntent;
import vn.ticketscenter.payment.dao.PaymentRepository.PaymentRecord;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PaymentServiceImpl implements PaymentService {
    private static final Logger LOGGER = Logger.getLogger(PaymentService.class.getName());
    private static final Duration KEY_CLAIM_TTL = Duration.ofMinutes(10);
    private static final Duration ZERO_ORDER_REPLAY_TTL = Duration.ofMinutes(10);

    private static final String SUCCESS = "Giao dịch thanh toán thành công.";
    private static final String CONFIRMING =
            "Giao dịch thanh toán thành công. Hệ thống đang xác nhận, vé sẽ xuất hiện sau ít phút.";
    private static final String FAILED = "Giao dịch không thành công hoặc bị người dùng hủy.";
    private static final String PENDING =
            "Giao dịch chưa hoàn tất. Hệ thống sẽ tự đối soát với payOS.";

    private final PaymentRepository repository;
    private final PayosGateway payos;
    private final RedisPaymentIdempotency idempotency;
    private final Clock clock;

    /** For test doubles that override every public method. */
    protected PaymentServiceImpl() {
        this.repository = null;
        this.payos = null;
        this.idempotency = null;
        this.clock = Clock.systemUTC();
    }

    public PaymentServiceImpl(
            PaymentRepository repository,
            PayosGateway payos,
            RedisPaymentIdempotency idempotency,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.payos = Objects.requireNonNull(payos, "payos is required");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency is required");
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    // ---------------------------------------------------------------- 1. Start paying an order

    @Override
    public PaymentInitiationResult initiate(UUID actorId, UUID orderId, UUID idempotencyKey) {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");

        // The key may only ever be used for this buyer + this order ('|' is the cache's own
        // separator).
        String fingerprint = actorId + ":" + orderId;
        RedisPaymentIdempotency.Claim claim;
        switch (claimKey(actorId, idempotencyKey, fingerprint)) {
            case RedisPaymentIdempotency.Replay replay -> {
                return PaymentInitiationResult.replay(replay.cachedBody());
            }
            case RedisPaymentIdempotency.Conflict conflict ->
                    throw PaymentException.idempotencyConflict();
            case RedisPaymentIdempotency.InProgress inProgress ->
                    throw new PaymentException(
                            409,
                            "PAYMENT_IN_PROGRESS",
                            "Payment initiation is in progress, please retry shortly.");
            case RedisPaymentIdempotency.Acquired acquired -> claim = acquired.claim();
        }

        try (AutoCloseable orderLock = idempotency.acquireOrderLock(orderId)) {
            if (orderLock == null) {
                claim.cancel();
                throw new PaymentException(
                        409,
                        "CONCURRENT_REQUEST",
                        "Another request is modifying this order, please retry.");
            }
            return startPayment(actorId, orderId, claim);
        } catch (PaymentException failure) {
            throw failure;
        } catch (Exception lockFailure) {
            claim.cancel();
            throw PaymentException.unavailable();
        }
    }

    // ---------------------------------------------------------------- 2. payOS webhook

    @Override
    public PayosWebhookResult processWebhook(String body) {
        PayosGateway.WebhookEvent event;
        try {
            event = payos.verifyWebhook(body);
        } catch (PayosGateway.InvalidWebhook invalid) {
            return PayosWebhookResult.invalidSignature();
        }
        if (!"00".equals(event.code())) {
            return PayosWebhookResult.ignored();
        }

        Optional<PaymentRecord> found = findByOrderCode(event.orderCode());
        if (found.isEmpty()) {
            return PayosWebhookResult.orderNotFound();
        }
        PaymentRecord payment = found.get();

        if (idempotency.getCachedWebhook(payment.txnRef(), event.paymentLinkId()) != null) {
            return PayosWebhookResult.alreadyConfirmed();
        }
        if (payment.amount().compareTo(BigDecimal.valueOf(event.amount())) != 0) {
            return PayosWebhookResult.invalidAmount();
        }

        PayosWebhookResult result;
        if ("CAPTURED".equals(payment.paymentStatus())) {
            result = PayosWebhookResult.alreadyConfirmed();
        } else {
            apply(payment, new PayosGateway.Result(Outcome.CAPTURED, event.paymentLinkId()));
            result = PayosWebhookResult.confirmed();
        }
        idempotency.cacheWebhook(payment.txnRef(), event.paymentLinkId(), result.toJson());
        return result;
    }

    // ---------------------------------------------------------------- 3. Buyer back from payOS

    @Override
    public PaymentReturnResult processReturn(Map<String, String> params) {
        Optional<Long> orderCode = parseOrderCode(params == null ? null : params.get("orderCode"));
        if (orderCode.isEmpty()) {
            return PaymentReturnResult.invalid("Tham số giao dịch không hợp lệ.");
        }

        Optional<PaymentRecord> found = findByOrderCode(orderCode.get());
        if (found.isEmpty()) {
            return PaymentReturnResult.invalid("Không tìm thấy giao dịch.");
        }
        PaymentRecord payment = found.get();

        if ("CAPTURED".equals(payment.paymentStatus())) {
            return returned(payment, true, Outcome.CAPTURED, SUCCESS);
        }

        PayosGateway.Result verified = payos.query(payment.txnRef(), payment.amount(), false);
        return switch (verified.outcome()) {
            case CAPTURED ->
                    returned(
                            payment,
                            true,
                            Outcome.CAPTURED,
                            applyQuietly(payment, verified) ? SUCCESS : CONFIRMING);
            case FAILED -> {
                applyQuietly(payment, verified);
                yield returned(payment, true, Outcome.FAILED, FAILED);
            }
            case UNKNOWN -> returned(payment, false, Outcome.UNKNOWN, PENDING);
        };
    }

    // ---------------------------------------------------------------- 4. Payment status

    @Override
    public PaymentStatusResult getOrderPaymentStatus(UUID actorId, UUID orderId) {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        return repository
                .getOrderPaymentStatus(orderId, actorId)
                .orElseThrow(PaymentException::orderNotFound);
    }

    // ---------------------------------------------------------------- Helpers: initiate

    private RedisPaymentIdempotency.Result claimKey(UUID actorId, UUID key, String fingerprint) {
        try {
            return idempotency.checkOrClaim(actorId, key, fingerprint, KEY_CLAIM_TTL);
        } catch (redis.clients.jedis.exceptions.JedisException redisDown) {
            throw PaymentException.unavailable();
        }
    }

    /** Runs under the order lock: open (or reuse) the DB payment, then its payOS link; cache the answer. */
    private PaymentInitiationResult startPayment(
            UUID actorId, UUID orderId, RedisPaymentIdempotency.Claim claim) {
        try {
            PaymentIntent intent = repository.beginOrderPayment(orderId, actorId);
            String checkoutUrl = createCheckoutLink(intent);
            PaymentInitiationResult result =
                    PaymentInitiationResult.redirect(
                            intent.paymentId(), intent.txnRef(), intent.amount(), checkoutUrl);
            claim.complete(result.body(), intent.holdRemaining());
            return result;
        } catch (PaymentRepository.ZeroOrderDetectedException zeroOrder) {
            repository.completeZeroOrder(orderId, actorId);
            PaymentInitiationResult result = PaymentInitiationResult.zero(orderId);
            claim.complete(result.body(), ZERO_ORDER_REPLAY_TTL);
            return result;
        } catch (RuntimeException failure) {
            claim.cancel();
            throw failure;
        }
    }

    /** The payOS link expires together with the seat hold, so nobody pays for seats already released. */
    private String createCheckoutLink(PaymentIntent intent) {
        try {
            long orderCode = PayosOrderCodes.fromTxnRef(intent.txnRef());
            return payos.createLink(
                            orderCode,
                            intent.amount().longValueExact(),
                            "TC" + orderCode,
                            clock.instant().plus(intent.holdRemaining()))
                    .checkoutUrl();
        } catch (RuntimeException payosFailure) {
            LOGGER.log(
                    Level.WARNING,
                    "payOS link could not be created for payment " + intent.paymentId(),
                    payosFailure);
            throw PaymentException.unavailable();
        }
    }

    // ---------------------------------------------------------------- Helpers: settle

    private Optional<PaymentRecord> findByOrderCode(long orderCode) {
        try {
            return repository.findPaymentByTxnRefPrefix(PayosOrderCodes.txnRefPrefix(orderCode));
        } catch (IllegalArgumentException notOurOrderCode) {
            return Optional.empty();
        }
    }

    /** The procedure locks its rows itself; the Redis order lock only keeps concurrent writers apart. */
    private void apply(PaymentRecord payment, PayosGateway.Result result) {
        try (AutoCloseable orderLock = idempotency.acquireOrderLock(payment.orderId())) {
            repository.applyPaymentResult(
                    payment.orderId(),
                    payment.paymentId(),
                    result.outcome().name(),
                    result.providerReference());
        } catch (PaymentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw PaymentException.unavailable();
        }
    }

    private boolean applyQuietly(PaymentRecord payment, PayosGateway.Result result) {
        try {
            apply(payment, result);
            return true;
        } catch (RuntimeException failure) {
            LOGGER.log(
                    Level.WARNING,
                    "Payment " + payment.paymentId() + " will be settled by webhook/reconciliation",
                    failure);
            return false;
        }
    }

    private static Optional<Long> parseOrderCode(String raw) {
        if (raw == null || !raw.trim().matches("[0-9]{1,16}")) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(raw.trim()));
    }

    private static PaymentReturnResult returned(
            PaymentRecord payment, boolean verified, Outcome outcome, String message) {
        return new PaymentReturnResult(
                verified,
                outcome == Outcome.CAPTURED,
                outcome.name(),
                payment.txnRef(),
                Optional.of(payment.orderId()),
                payment.amount(),
                message);
    }
}
