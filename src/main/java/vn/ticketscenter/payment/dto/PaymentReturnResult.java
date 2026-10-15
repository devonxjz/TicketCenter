package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * What the buyer sees after payOS redirects back. {@code verified} means payOS itself was asked for the
 * payment status: the query string of a return/cancel URL is not signed and is never trusted.
 */
public record PaymentReturnResult(
        boolean verified,
        boolean success,
        String status,
        String txnRef,
        Optional<UUID> orderId,
        BigDecimal amount,
        String message
) {
    public static PaymentReturnResult invalid(String message) {
        return new PaymentReturnResult(false, false, "", "", Optional.empty(), BigDecimal.ZERO, message);
    }

    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("verified", verified);
        json.put("success", success);
        json.put("status", status != null ? status : "");
        json.put("txnRef", txnRef != null ? txnRef : "");
        json.put("orderId", orderId.map(UUID::toString).orElse(null));
        json.put("amount", amount != null ? amount.toPlainString() : "0");
        json.put("message", message != null ? message : "");
        return json.toString();
    }
}
