package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;

/** Generates six-digit OTP codes and the HMAC-SHA256 hash stored in {@code secret_hash} (varbinary(32)). */
public class OtpHasher {

    static final String DEFAULT_SECRET = "TicketsCenterDefaultOtpSecretKey2026";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int HASH_LENGTH = 32;

    private final HmacSha256 hmac;

    public OtpHasher() {
        this(AppConfig.get(AppConfig.Keys.APP_SECRET_KEY, DEFAULT_SECRET));
    }

    public OtpHasher(String secretKey) {
        this.hmac = new HmacSha256(secretKey, DEFAULT_SECRET);
    }

    /** A random code in [000000, 999999]. */
    public String generateOtpCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    public byte[] hashOtp(String code) {
        Objects.requireNonNull(code, "OTP code must not be null");
        return hmac.compute(code);
    }

    /** Compares in constant time. */
    public boolean verifyOtpHash(String rawOtp, byte[] storedHash) {
        if (rawOtp == null || storedHash == null || storedHash.length != HASH_LENGTH) {
            return false;
        }
        return MessageDigest.isEqual(hashOtp(rawOtp), storedHash);
    }
}
