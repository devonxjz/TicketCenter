package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

/**
 * Body returned to the payOS webhook. payOS only needs a 2xx to stop retrying, so every verified
 * delivery answers 200 (an unknown order code included: payOS sends one when confirming the URL);
 * only an unverifiable signature answers 400.
 */
public record PayosWebhookResult(int httpStatus, int error, String message) {
    public static PayosWebhookResult confirmed() {
        return new PayosWebhookResult(200, 0, "Webhook delivered");
    }

    public static PayosWebhookResult alreadyConfirmed() {
        return new PayosWebhookResult(200, 0, "Order already confirmed");
    }

    public static PayosWebhookResult ignored() {
        return new PayosWebhookResult(200, 0, "Webhook ignored: payment not successful");
    }

    public static PayosWebhookResult orderNotFound() {
        return new PayosWebhookResult(200, 1, "Order not found");
    }

    public static PayosWebhookResult invalidAmount() {
        return new PayosWebhookResult(200, 4, "Invalid amount");
    }

    public static PayosWebhookResult invalidSignature() {
        return new PayosWebhookResult(400, 97, "Invalid signature");
    }

    public String toJson() {
        return new JSONObject().put("error", error).put("message", message).toString();
    }
}
