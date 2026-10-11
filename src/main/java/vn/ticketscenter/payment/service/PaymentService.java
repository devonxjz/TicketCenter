package vn.ticketscenter.payment.service;

import vn.ticketscenter.payment.cache.RedisPaymentIdempotency;
import vn.ticketscenter.payment.dto.InitiatePaymentRequest;
import vn.ticketscenter.payment.dto.PaymentActor;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.repository.PaymentRepository;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PaymentService {
    private final PaymentRepository repository;
    private final VnpaySigner signer;
    private final RedisPaymentIdempotency idempotency;

    protected PaymentService() {
        this.repository = null;
        this.signer = null;
        this.idempotency = null;
    }

    public PaymentService(
            PaymentRepository repository,
            VnpaySigner signer,
            RedisPaymentIdempotency idempotency
    ) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.signer = Objects.requireNonNull(signer, "signer is required");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency is required");
    }

    public PaymentInitiationResult initiate(
            PaymentActor actor,
            UUID orderId,
            UUID idempotencyKey,
            Optional<String> bankCode,
            String clientIp
    ) {
        Objects.requireNonNull(actor, "actor is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");

        InitiatePaymentRequest request = new InitiatePaymentRequest(bankCode);
        String fingerprint = request.fingerprint(actor.id(), orderId);

        var check = idempotency.checkOrClaim(actor.id(), idempotencyKey, fingerprint, Duration.ofMinutes(10));
        if (check instanceof RedisPaymentIdempotency.Replay replay) {
            return PaymentInitiationResult.fromReplay(replay.cachedBody());
        }
        if (check instanceof RedisPaymentIdempotency.Conflict) {
            throw PaymentException.idempotencyConflict();
        }
        if (check instanceof RedisPaymentIdempotency.InProgress) {
            throw new PaymentException(409, "PAYMENT_IN_PROGRESS", "Payment initiation is in progress, please retry shortly.");
        }

        RedisPaymentIdempotency.Acquired acquired = (RedisPaymentIdempotency.Acquired) check;
        RedisPaymentIdempotency.Claim claim = acquired.claim();

        try (AutoCloseable lock = idempotency.acquireOrderLock(orderId)) {
            if (lock == null) {
                claim.cancel();
                throw new PaymentException(409, "CONCURRENT_REQUEST", "Another request is modifying this order, please retry.");
            }

            try {
                PaymentRepository.PaymentIntent intent = repository.beginOrderPayment(orderId, actor.id());
                String orderInfo = "Thanh toan don hang " + intent.orderCode();
                String redirectUrl = signer.buildPaymentUrl(intent.txnRef(), intent.amount(), orderInfo, clientIp, bankCode);

                PaymentInitiationResult result = PaymentInitiationResult.redirect(
                        intent.paymentId(),
                        intent.txnRef(),
                        intent.amount(),
                        intent.currency(),
                        redirectUrl,
                        false
                );

                claim.complete(result.body(), intent.holdRemaining());
                return result;
            } catch (PaymentRepository.ZeroOrderDetectedException zero) {
                repository.completeZeroOrder(orderId, actor.id());
                PaymentInitiationResult result = PaymentInitiationResult.zero(orderId, false);
                claim.complete(result.body(), Duration.ofMinutes(10));
                return result;
            } catch (RuntimeException failure) {
                claim.cancel();
                throw failure;
            }
        } catch (PaymentException failure) {
            throw failure;
        } catch (Exception lockFailure) {
            claim.cancel();
            throw PaymentException.unavailable();
        }
    }
}
