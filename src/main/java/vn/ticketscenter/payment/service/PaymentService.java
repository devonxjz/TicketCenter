package vn.ticketscenter.payment.service;

import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.dto.PaymentReturnResult;
import vn.ticketscenter.payment.dto.PaymentStatusResult;
import vn.ticketscenter.payment.dto.PayosWebhookResult;
import java.util.Map;
import java.util.UUID;

/**
 * payOS payment flow:
 * <ol>
 *   <li>{@link #initiate}: the buyer starts paying an order (Idempotency-Key required) and gets the payOS checkout URL;</li>
 *   <li>{@link #processWebhook}: payOS reports a successful transfer (signed with the checksum key);</li>
 *   <li>{@link #processReturn}: the buyer comes back from payOS; the status is asked again from payOS.</li>
 * </ol>
 * Payment truth is written only by {@code usp_ApplyPaymentResult}; no transaction is held while payOS is called.
 */
public interface PaymentService {
    PaymentInitiationResult initiate(UUID actorId, UUID orderId, UUID idempotencyKey);

    PayosWebhookResult processWebhook(String body);

    /**
     * Handles both returnUrl and cancelUrl. Their query string is not signed, so only {@code orderCode} is read
     * and the real status is asked from payOS. Settling here issues tickets even when the webhook cannot reach
     * this server (localhost); if it fails, the webhook or the reconciliation job settles later.
     */
    PaymentReturnResult processReturn(Map<String, String> params);

    PaymentStatusResult getOrderPaymentStatus(UUID actorId, UUID orderId);
}
