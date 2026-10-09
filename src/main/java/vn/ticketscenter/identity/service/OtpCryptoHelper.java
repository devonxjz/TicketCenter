package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

public class OtpCryptoHelper {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String DEFAULT_SECRET = "TicketsCenterDefaultOtpSecretKey2026";

    private final byte[] secretKeyBytes;

    public record ResetTokenClaims(UUID userId, Instant expiresAt) {}

    public OtpCryptoHelper() {
        this(AppConfig.get(AppConfig.Keys.APP_SECRET_KEY, DEFAULT_SECRET));
    }

    public OtpCryptoHelper(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = DEFAULT_SECRET;
        }
        this.secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Sinh mã OTP 6 chữ số ngẫu nhiên [000000 - 999999].
     */
    public String generateOtpCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    /**
     * Băm mã OTP bằng HMAC-SHA256 để lưu trữ vào secret_hash (varbinary(32)).
     */
    public byte[] hashOtp(String code) {
        Objects.requireNonNull(code, "OTP code must not be null");
        return computeHmac(code.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Xác thực mã OTP thô với mã băm lưu trong database bằng so sánh constant-time.
     */
    public boolean verifyOtpHash(String rawOtp, byte[] storedHash) {
        if (rawOtp == null || storedHash == null || storedHash.length != 32) {
            return false;
        }
        byte[] computedHash = hashOtp(rawOtp);
        return MessageDigest.isEqual(computedHash, storedHash);
    }

    /**
     * Tạo stateless reset token có chữ ký HMAC tự vô hiệu hóa qua passwordHash và authVersion.
     * Cấu trúc: <userId>.<expiresAtEpochMilli>.<signatureBase64Url>
     */
    public String generateResetToken(UUID userId, Instant expiresAt, String passwordHash, int authVersion) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");

        String payload = buildResetTokenPayload(userId, expiresAt.toEpochMilli(), passwordHash, authVersion);
        byte[] signature = computeHmac(payload.getBytes(StandardCharsets.UTF_8));
        String signatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        return userId + "." + expiresAt.toEpochMilli() + "." + signatureBase64;
    }

    /**
     * Tách userId từ token mà không cần kiểm tra chữ ký trước (dùng để load User từ DB).
     */
    public UUID extractUserIdFromToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid reset token format");
        }
        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user ID in reset token", e);
        }
    }

    /**
     * Xác thực reset token với thông tin xác thực hiện tại của user trong database.
     */
    public ResetTokenClaims parseAndVerifyResetToken(String token, UUID expectedUserId,
                                                    String currentPasswordHash, int currentAuthVersion) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token must not be blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid reset token format");
        }

        UUID tokenUserId;
        long expiresAtMilli;
        try {
            tokenUserId = UUID.fromString(parts[0]);
            expiresAtMilli = Long.parseLong(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Malformed reset token parts", e);
        }

        if (!tokenUserId.equals(expectedUserId)) {
            throw new SecurityException("TOKEN_USER_MISMATCH");
        }

        Instant expiresAt = Instant.ofEpochMilli(expiresAtMilli);
        if (Instant.now().isAfter(expiresAt)) {
            throw new SecurityException("RESET_TOKEN_EXPIRED");
        }

        String payload = buildResetTokenPayload(expectedUserId, expiresAtMilli, currentPasswordHash, currentAuthVersion);
        byte[] expectedSig = computeHmac(payload.getBytes(StandardCharsets.UTF_8));
        String expectedSigBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSig);

        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSigBase64.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("RESET_TOKEN_INVALID");
        }

        return new ResetTokenClaims(tokenUserId, expiresAt);
    }

    private String buildResetTokenPayload(UUID userId, long expiresAtMilli, String passwordHash, int authVersion) {
        return userId + ":" + expiresAtMilli + ":" + passwordHash + ":" + authVersion;
    }

    private byte[] computeHmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKeyBytes, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Crypto error computing HMAC-SHA256", e);
        }
    }
}
