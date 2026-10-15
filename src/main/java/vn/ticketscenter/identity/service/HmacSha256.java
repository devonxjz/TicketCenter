package vn.ticketscenter.identity.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/** HMAC-SHA256 keyed with the application secret; shared by OTP hashing and reset-token signing. */
final class HmacSha256 {

    private static final String ALGORITHM = "HmacSHA256";

    private final byte[] key;

    HmacSha256(String secret, String fallbackSecret) {
        String effective = secret == null || secret.isBlank() ? fallbackSecret : secret;
        this.key = effective.getBytes(StandardCharsets.UTF_8);
    }

    byte[] compute(byte[] data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Crypto error computing HMAC-SHA256", e);
        }
    }

    byte[] compute(String data) {
        return compute(data.getBytes(StandardCharsets.UTF_8));
    }
}
