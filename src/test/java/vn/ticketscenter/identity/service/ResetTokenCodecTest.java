package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ResetTokenCodecTest {

    private static final String PASSWORD_HASH = "salt:hash123";

    private ResetTokenCodec codec;

    @BeforeEach
    void setUp() {
        codec = new ResetTokenCodec("test-secret-key-ticketscenter-super-secure-2026");
    }

    private static IdentityError errorOf(Runnable action) {
        return assertThrows(IdentityException.class, action::run).error();
    }

    @Test
    @DisplayName("generate tạo token stateless và verify kiểm tra hợp lệ")
    void shouldGenerateAndVerifyResetToken() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        String token = codec.generate(userId, expiresAt, PASSWORD_HASH, 1);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
        assertEquals(userId, codec.extractUserId(token));

        ResetTokenCodec.Claims claims = codec.verify(token, userId, PASSWORD_HASH, 1);
        assertEquals(userId, claims.userId());
        assertEquals(expiresAt.toEpochMilli(), claims.expiresAt().toEpochMilli());
    }

    @Test
    @DisplayName("verify từ chối token đã hết hạn")
    void shouldRejectExpiredResetToken() {
        UUID userId = UUID.randomUUID();
        String token = codec.generate(userId, Instant.now().minus(1, ChronoUnit.MINUTES), PASSWORD_HASH, 1);

        assertEquals(IdentityError.INVALID_TOKEN, errorOf(() -> codec.verify(token, userId, PASSWORD_HASH, 1)));
    }

    @Test
    @DisplayName("verify tự động vô hiệu hóa khi passwordHash hoặc authVersion thay đổi")
    void shouldRevokeTokenWhenPasswordOrAuthVersionChanges() {
        UUID userId = UUID.randomUUID();
        String token = codec.generate(userId, Instant.now().plus(15, ChronoUnit.MINUTES), PASSWORD_HASH, 1);

        assertEquals(IdentityError.INVALID_TOKEN,
                errorOf(() -> codec.verify(token, userId, "salt:newhash456", 1)));
        assertEquals(IdentityError.INVALID_TOKEN,
                errorOf(() -> codec.verify(token, userId, PASSWORD_HASH, 2)));
    }

    @Test
    @DisplayName("verify từ chối token của người dùng khác")
    void shouldRejectTokenOfAnotherUser() {
        UUID userId = UUID.randomUUID();
        String token = codec.generate(userId, Instant.now().plus(15, ChronoUnit.MINUTES), PASSWORD_HASH, 1);

        assertEquals(IdentityError.INVALID_TOKEN,
                errorOf(() -> codec.verify(token, UUID.randomUUID(), PASSWORD_HASH, 1)));
    }

    @Test
    @DisplayName("verify từ chối token bị sửa đổi hoặc sai format")
    void shouldRejectTamperedOrMalformedToken() {
        UUID userId = UUID.randomUUID();
        String token = codec.generate(userId, Instant.now().plus(15, ChronoUnit.MINUTES), PASSWORD_HASH, 1);

        assertEquals(IdentityError.INVALID_TOKEN,
                errorOf(() -> codec.verify(token + "xyz", userId, PASSWORD_HASH, 1)));
        assertEquals(IdentityError.VALIDATION_FAILED,
                errorOf(() -> codec.verify("invalid.token", userId, PASSWORD_HASH, 1)));
        assertEquals(IdentityError.VALIDATION_FAILED,
                errorOf(() -> codec.verify(null, userId, PASSWORD_HASH, 1)));
        assertEquals(IdentityError.VALIDATION_FAILED,
                errorOf(() -> codec.extractUserId("not-a-uuid.123.sig")));
    }
}
