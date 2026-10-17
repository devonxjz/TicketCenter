package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record PaymentStatusResult(
        UUID orderId,
        String orderStatus,
        Optional<UUID> paymentId,
        Optional<String> paymentStatus,
        BigDecimal amount,
        String currency,
        Optional<Instant> paidAt) {
    public String toJson() {
        JSONObject json = new JSONObject();
        json.put("orderId", orderId.toString());
        json.put("orderStatus", orderStatus);
        json.put("paymentId", paymentId.map(UUID::toString).orElse(null));
        json.put("paymentStatus", paymentStatus.orElse(null));
        json.put("amount", amount != null ? amount.toPlainString() : "0");
        json.put("currency", currency != null ? currency : "VND");
        json.put("paidAt", paidAt.map(Instant::toString).orElse(null));
        return json.toString();
    }
}
