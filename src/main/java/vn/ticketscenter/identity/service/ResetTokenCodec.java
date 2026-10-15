package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Stateless password-reset tokens {@code <userId>.<expiresAtEpochMilli>.<signature>}. The signature covers the
 * current password hash and auth version, so using a token (or any other credential change) invalidates it.
 */
public class ResetTokenCodec {

    public record Claims(UUID userId, Instant expiresAt) { }

    private final HmacSha256 hmac;

    public ResetTokenCodec() {
        this(AppConfig.get(AppConfig.Keys.APP_SECRET_KEY, OtpHasher.DEFAULT_SECRET));
    }

    public ResetTokenCodec(String secretKey) {
        this.hmac = new HmacSha256(secretKey, OtpHasher.DEFAULT_SECRET);
    }

    public String generate(UUID userId, Instant expiresAt, String passwordHash, int authVersion) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(passwordHash, "passwordHash must not be null");
        long expiresAtMilli = expiresAt.toEpochMilli();
        return userId + "." + expiresAtMilli + "." + sign(userId, expiresAtMilli, passwordHash, authVersion);
    }

    /** Reads the user id without checking the signature, so the user can be loaded to verify against. */
    public UUID extractUserId(String token) {
        String[] parts = split(token);
        try {
            return UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw validation("Invalid user ID in reset token", e);
        }
    }

    /**
     * @throws IdentityException {@code VALIDATION_FAILED} for a malformed token, {@code INVALID_TOKEN} for a
     *     wrong user, an expired token or a bad signature
     */
    public Claims verify(String token, UUID expectedUserId, String currentPasswordHash, int currentAuthVersion) {
        String[] parts = split(token);
        UUID tokenUserId;
        long expiresAtMilli;
        try {
            tokenUserId = UUID.fromString(parts[0]);
            expiresAtMilli = Long.parseLong(parts[1]);
        } catch (RuntimeException e) {
            throw validation("Malformed reset token parts", e);
        }
        if (!tokenUserId.equals(expectedUserId)) {
            throw rejected("TOKEN_USER_MISMATCH");
        }
        Instant expiresAt = Instant.ofEpochMilli(expiresAtMilli);
        if (Instant.now().isAfter(expiresAt)) {
            throw rejected("RESET_TOKEN_EXPIRED");
        }
        String expected = sign(expectedUserId, expiresAtMilli, currentPasswordHash, currentAuthVersion);
        if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) {
            throw rejected("RESET_TOKEN_INVALID");
        }
        return new Claims(tokenUserId, expiresAt);
    }

    private String sign(UUID userId, long expiresAtMilli, String passwordHash, int authVersion) {
        String payload = userId + ":" + expiresAtMilli + ":" + passwordHash + ":" + authVersion;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hmac.compute(payload));
    }

    private static String[] split(String token) {
        if (token == null || token.isBlank()) {
            throw validation("Token must not be blank", null);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw validation("Invalid reset token format", null);
        }
        return parts;
    }

    private static IdentityException validation(String message, Throwable cause) {
        return new IdentityException(IdentityError.VALIDATION_FAILED, message, cause);
    }

    private static IdentityException rejected(String message) {
        return new IdentityException(IdentityError.INVALID_TOKEN, message);
    }
}
