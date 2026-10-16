package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Response of {@code POST /api/orders/{orderId}/payments}. The JSON {@code body} is also what the
 * idempotency cache stores, so a replay returns exactly the first answer (with {@code replayed=true}).
 */
public record PaymentInitiationResult(
        String body, boolean replayed, boolean zeroOrder, Optional<String> redirectUrl) {

    /** Paid order: the buyer is sent to the payOS checkout page. */
    public static PaymentInitiationResult redirect(
            UUID paymentId, String txnRef, BigDecimal amount, String checkoutUrl) {
        JSONObject json =
                new JSONObject()
                        .put("paymentId", paymentId.toString())
                        .put("txnRef", txnRef)
                        .put("amount", amount.toPlainString())
                        .put("currency", "VND")
                        .put("redirectUrl", checkoutUrl)
                        .put("replayed", false)
                        .put("zeroOrder", false);
        return new PaymentInitiationResult(json.toString(), false, false, Optional.of(checkoutUrl));
    }

    /** 0 VND order: completed directly, payOS is never called. */
    public static PaymentInitiationResult zero(UUID orderId) {
        JSONObject json =
                new JSONObject()
                        .put("orderId", orderId.toString())
                        .put("amount", "0")
                        .put("currency", "VND")
                        .put("status", "PAID")
                        .put("replayed", false)
                        .put("zeroOrder", true);
        return new PaymentInitiationResult(json.toString(), false, true, Optional.empty());
    }

    /** Same Idempotency-Key and same request again: answer from the cached body. */
    public static PaymentInitiationResult replay(String cachedBody) {
        JSONObject json = new JSONObject(cachedBody).put("replayed", true);
        return new PaymentInitiationResult(
                json.toString(),
                true,
                json.optBoolean("zeroOrder"),
                Optional.ofNullable(json.optString("redirectUrl", null)));
    }
}
