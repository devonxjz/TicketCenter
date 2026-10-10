package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OtpCryptoHelperTest {

    private OtpCryptoHelper helper;
    private final String secretKey = "test-secret-key-ticketscenter-super-secure-2026";

    @BeforeEach
    void setUp() {
        helper = new OtpCryptoHelper(secretKey);
    }

    @Test
    @DisplayName("generateOtpCode sinh chuỗi 6 chữ số hợp lệ")
    void shouldGenerateValid6DigitOtp() {
        for (int i = 0; i < 50; i++) {
            String code = helper.generateOtpCode();
            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(code.matches("^[0-9]{6}$"), "OTP phải là 6 chữ số: " + code);
        }
    }

    @Test
    @DisplayName("hashOtp sinh byte array 32 bytes từ HMAC-SHA256")
    void shouldHashOtpTo32Bytes() {
        String code = "123456";
        byte[] hash = helper.hashOtp(code);

        assertNotNull(hash);
        assertEquals(32, hash.length, "HMAC-SHA256 phải có độ dài 32 bytes");
    }

    @Test
    @DisplayName("verifyOtpHash xác thực chính xác mã OTP và chống sai mã")
    void shouldVerifyOtpHashCorrectly() {
        String code = "654321";
        byte[] hash = helper.hashOtp(code);

        assertTrue(helper.verifyOtpHash(code, hash));
        assertFalse(helper.verifyOtpHash("654320", hash));
        assertFalse(helper.verifyOtpHash("111111", hash));
        assertFalse(helper.verifyOtpHash(null, hash));
        assertFalse(helper.verifyOtpHash(code, null));
        assertFalse(helper.verifyOtpHash(code, new byte[32]));
    }

    @Test
    @DisplayName("generateResetToken tạo token stateless và verifyResetToken kiểm tra hợp lệ")
    void shouldGenerateAndVerifyResetToken() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        String passwordHash = "salt:hash123";
        int authVersion = 1;

        String token = helper.generateResetToken(userId, expiresAt, passwordHash, authVersion);
        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        OtpCryptoHelper.ResetTokenClaims claims = helper.parseAndVerifyResetToken(token, userId, passwordHash, authVersion);
        assertNotNull(claims);
        assertEquals(userId, claims.userId());
        assertEquals(expiresAt.toEpochMilli(), claims.expiresAt().toEpochMilli());
    }

    @Test
    @DisplayName("parseAndVerifyResetToken từ chối token đã hết hạn")
    void shouldRejectExpiredResetToken() {
        UUID userId = UUID.randomUUID();
        Instant expiredAt = Instant.now().minus(1, ChronoUnit.MINUTES);
        String passwordHash = "salt:hash123";
        int authVersion = 1;

        String token = helper.generateResetToken(userId, expiredAt, passwordHash, authVersion);

        assertThrows(SecurityException.class, () ->
                helper.parseAndVerifyResetToken(token, userId, passwordHash, authVersion));
    }

    @Test
    @DisplayName("parseAndVerifyResetToken tự động vô hiệu hóa khi passwordHash hoặc authVersion thay đổi")
    void shouldRevokeTokenWhenPasswordOrAuthVersionChanges() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        String oldPasswordHash = "salt:hash123";
        int oldAuthVersion = 1;

        String token = helper.generateResetToken(userId, expiresAt, oldPasswordHash, oldAuthVersion);

        // Trường hợp 1: passwordHash đã thay đổi
        assertThrows(SecurityException.class, () ->
                helper.parseAndVerifyResetToken(token, userId, "salt:newhash456", oldAuthVersion));

        // Trường hợp 2: authVersion đã tăng
        assertThrows(SecurityException.class, () ->
                helper.parseAndVerifyResetToken(token, userId, oldPasswordHash, oldAuthVersion + 1));
    }

    @Test
    @DisplayName("parseAndVerifyResetToken từ chối token bị sửa đổi hoặc sai format")
    void shouldRejectTamperedOrMalformedToken() {
        UUID userId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);
        String passwordHash = "salt:hash123";
        int authVersion = 1;

        String token = helper.generateResetToken(userId, expiresAt, passwordHash, authVersion);
        String tamperedToken = token + "xyz";

        assertThrows(SecurityException.class, () ->
                helper.parseAndVerifyResetToken(tamperedToken, userId, passwordHash, authVersion));
        assertThrows(IllegalArgumentException.class, () ->
                helper.parseAndVerifyResetToken("invalid.token", userId, passwordHash, authVersion));
        assertThrows(IllegalArgumentException.class, () ->
                helper.parseAndVerifyResetToken(null, userId, passwordHash, authVersion));
    }
}
