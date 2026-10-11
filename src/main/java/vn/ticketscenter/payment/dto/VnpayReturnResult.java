package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public record VnpayReturnResult(
        boolean validSignature,
        boolean success,
        String responseCode,
        String txnRef,
        Optional<UUID> orderId,
        BigDecimal amount,
        String message
) {
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("validSignature", validSignature);
        json.put("success", success);
        json.put("responseCode", responseCode != null ? responseCode : "");
        json.put("txnRef", txnRef != null ? txnRef : "");
        json.put("orderId", orderId.map(UUID::toString).orElse(null));
        json.put("amount", amount != null ? amount.toPlainString() : "0");
        json.put("message", message != null ? message : "");
        return json.toString();
    }
}
