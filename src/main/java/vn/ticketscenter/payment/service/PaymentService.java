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

    public vn.ticketscenter.payment.dto.VnpayIpnResult processIpn(java.util.Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return vn.ticketscenter.payment.dto.VnpayIpnResult.invalidChecksum();
        }
        String secureHash = queryParams.get("vnp_SecureHash");
        if (secureHash == null || !signer.verifySignature(queryParams, secureHash)) {
            return vn.ticketscenter.payment.dto.VnpayIpnResult.invalidChecksum();
        }

        String txnRef = queryParams.get("vnp_TxnRef");
        if (txnRef == null || txnRef.isBlank()) {
            return vn.ticketscenter.payment.dto.VnpayIpnResult.orderNotFound();
        }

        String providerTxnNo = queryParams.get("vnp_TransactionNo");
        String responseCode = queryParams.get("vnp_ResponseCode");
        String transactionStatus = queryParams.get("vnp_TransactionStatus");
        String amountStr = queryParams.get("vnp_Amount");

        String cached = idempotency.getCachedIpn(txnRef, providerTxnNo);
        if (cached != null) {
            return vn.ticketscenter.payment.dto.VnpayIpnResult.orderAlreadyConfirmed();
        }

        Optional<PaymentRepository.PaymentRecord> maybePayment = repository.findPaymentByTxnRef(txnRef);
        if (maybePayment.isEmpty()) {
            return vn.ticketscenter.payment.dto.VnpayIpnResult.orderNotFound();
        }
        PaymentRepository.PaymentRecord payment = maybePayment.get();

        if (amountStr != null) {
            try {
                java.math.BigDecimal receivedCents = new java.math.BigDecimal(amountStr.trim());
                java.math.BigDecimal expectedCents = payment.amount().multiply(java.math.BigDecimal.valueOf(100));
                if (receivedCents.compareTo(expectedCents) != 0) {
                    return vn.ticketscenter.payment.dto.VnpayIpnResult.invalidAmount();
                }
            } catch (NumberFormatException failure) {
                return vn.ticketscenter.payment.dto.VnpayIpnResult.invalidAmount();
            }
        }

        if ("CAPTURED".equals(payment.paymentStatus())) {
            vn.ticketscenter.payment.dto.VnpayIpnResult confirmed = vn.ticketscenter.payment.dto.VnpayIpnResult.orderAlreadyConfirmed();
            idempotency.cacheIpn(txnRef, providerTxnNo, confirmed.toJson());
            return confirmed;
        }

        try (AutoCloseable lock = idempotency.acquireOrderLock(payment.orderId())) {
            boolean isSuccess = "00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null);
            String targetStatus = isSuccess ? "CAPTURED" : "FAILED";
            repository.applyPaymentResult(payment.orderId(), payment.paymentId(), targetStatus, providerTxnNo);

            vn.ticketscenter.payment.dto.VnpayIpnResult result = vn.ticketscenter.payment.dto.VnpayIpnResult.success();
            idempotency.cacheIpn(txnRef, providerTxnNo, result.toJson());
            return result;
        } catch (PaymentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw PaymentException.unavailable();
        }
    }

    public vn.ticketscenter.payment.dto.VnpayReturnResult processReturn(java.util.Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return new vn.ticketscenter.payment.dto.VnpayReturnResult(
                    false, false, "", "", Optional.empty(), java.math.BigDecimal.ZERO, "Tham số giao dịch không hợp lệ."
            );
        }
        String secureHash = queryParams.get("vnp_SecureHash");
        boolean validSignature = secureHash != null && signer.verifySignature(queryParams, secureHash);

        String txnRef = queryParams.get("vnp_TxnRef");
        String responseCode = queryParams.get("vnp_ResponseCode");
        String amountStr = queryParams.get("vnp_Amount");
        java.math.BigDecimal amount = java.math.BigDecimal.ZERO;
        if (amountStr != null) {
            try {
                amount = new java.math.BigDecimal(amountStr.trim()).divide(java.math.BigDecimal.valueOf(100));
            } catch (NumberFormatException ignored) { }
        }

        Optional<PaymentRepository.PaymentRecord> maybePayment = (txnRef != null && !txnRef.isBlank())
                ? repository.findPaymentByTxnRef(txnRef)
                : Optional.empty();

        boolean success = validSignature && "00".equals(responseCode);
        String message = success
                ? "Giao dịch thanh toán thành công."
                : (validSignature ? "Giao dịch không thành công hoặc bị người dùng hủy." : "Chữ ký giao dịch không hợp lệ.");

        return new vn.ticketscenter.payment.dto.VnpayReturnResult(
                validSignature,
                success,
                responseCode,
                txnRef,
                maybePayment.map(PaymentRepository.PaymentRecord::orderId),
                amount,
                message
        );
    }

    public vn.ticketscenter.payment.dto.PaymentStatusResult getOrderPaymentStatus(UUID actorId, UUID orderId) {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(orderId, "orderId is required");
        return repository.getOrderPaymentStatus(orderId, actorId)
                .orElseThrow(PaymentException::orderNotFound);
    }
}
