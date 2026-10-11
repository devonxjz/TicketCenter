package vn.ticketscenter.payment.dto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public record InitiatePaymentRequest(Optional<String> bankCode) {
    public InitiatePaymentRequest {
        if (bankCode == null) {
            bankCode = Optional.empty();
        }
        if (bankCode.isPresent()) {
            String code = bankCode.get().trim();
            if (code.isEmpty()) {
                bankCode = Optional.empty();
            } else {
                if (!code.matches("[A-Za-z0-9_]{1,20}")) {
                    throw new IllegalArgumentException("invalid bankCode: " + code);
                }
                bankCode = Optional.of(code);
            }
        }
    }

    public static InitiatePaymentRequest of(String bankCode) {
        return new InitiatePaymentRequest(Optional.ofNullable(bankCode));
    }

    public static InitiatePaymentRequest empty() {
        return new InitiatePaymentRequest(Optional.empty());
    }

    public String fingerprint(UUID actorId, UUID orderId) {
        try {
            String raw = actorId.toString() + "|" + orderId.toString() + "|" + bankCode.orElse("");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
