package vn.ticketscenter.payment.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PaymentInitiationResult(
        int httpStatus,
        String body,
        boolean replayed,
        Optional<UUID> paymentId,
        Optional<String> txnRef,
        BigDecimal amount,
        String currency,
        Optional<String> redirectUrl,
        boolean zeroOrder
) {
    public PaymentInitiationResult {
        Objects.requireNonNull(body, "body is required");
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        Objects.requireNonNull(paymentId, "paymentId is required");
        Objects.requireNonNull(txnRef, "txnRef is required");
        Objects.requireNonNull(redirectUrl, "redirectUrl is required");
    }

    public static PaymentInitiationResult redirect(
            UUID paymentId,
            String txnRef,
            BigDecimal amount,
            String currency,
            String redirectUrl,
            boolean replayed
    ) {
        JSONObject json = new JSONObject();
        json.put("paymentId", paymentId.toString());
        json.put("txnRef", txnRef);
        json.put("amount", amount.toPlainString());
        json.put("currency", currency);
        json.put("redirectUrl", redirectUrl);
        json.put("replayed", replayed);
        json.put("zeroOrder", false);
        String body = json.toString();
        return new PaymentInitiationResult(
                200,
                body,
                replayed,
                Optional.of(paymentId),
                Optional.of(txnRef),
                amount,
                currency,
                Optional.of(redirectUrl),
                false
        );
    }

    public static PaymentInitiationResult zero(UUID orderId, boolean replayed) {
        JSONObject json = new JSONObject();
        json.put("orderId", orderId.toString());
        json.put("amount", "0");
        json.put("currency", "VND");
        json.put("status", "PAID");
        json.put("replayed", replayed);
        json.put("zeroOrder", true);
        String body = json.toString();
        return new PaymentInitiationResult(
                200,
                body,
                replayed,
                Optional.empty(),
                Optional.empty(),
                BigDecimal.ZERO,
                "VND",
                Optional.empty(),
                true
        );
    }

    public static PaymentInitiationResult fromReplay(String cachedBody) {
        JSONObject json = new JSONObject(cachedBody);
        boolean zero = json.optBoolean("zeroOrder", false);
        if (zero) {
            json.put("replayed", true);
            return new PaymentInitiationResult(
                    200,
                    json.toString(),
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    BigDecimal.ZERO,
                    json.optString("currency", "VND"),
                    Optional.empty(),
                    true
            );
        }
        json.put("replayed", true);
        UUID pid = UUID.fromString(json.getString("paymentId"));
        String txn = json.getString("txnRef");
        BigDecimal amt = new BigDecimal(json.getString("amount"));
        String curr = json.getString("currency");
        String url = json.getString("redirectUrl");
        return new PaymentInitiationResult(
                200,
                json.toString(),
                true,
                Optional.of(pid),
                Optional.of(txn),
                amt,
                curr,
                Optional.of(url),
                false
        );
    }
}
