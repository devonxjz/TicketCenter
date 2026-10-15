package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OtpHasherTest {

    private OtpHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new OtpHasher("test-secret-key-ticketscenter-super-secure-2026");
    }

    @Test
    @DisplayName("generateOtpCode sinh chuỗi 6 chữ số hợp lệ")
    void shouldGenerateValid6DigitOtp() {
        for (int i = 0; i < 50; i++) {
            String code = hasher.generateOtpCode();
            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(code.matches("^[0-9]{6}$"), "OTP phải là 6 chữ số: " + code);
        }
    }

    @Test
    @DisplayName("hashOtp sinh byte array 32 bytes từ HMAC-SHA256")
    void shouldHashOtpTo32Bytes() {
        byte[] hash = hasher.hashOtp("123456");

        assertNotNull(hash);
        assertEquals(32, hash.length, "HMAC-SHA256 phải có độ dài 32 bytes");
    }

    @Test
    @DisplayName("verifyOtpHash xác thực chính xác mã OTP và chống sai mã")
    void shouldVerifyOtpHashCorrectly() {
        String code = "654321";
        byte[] hash = hasher.hashOtp(code);

        assertTrue(hasher.verifyOtpHash(code, hash));
        assertFalse(hasher.verifyOtpHash("654320", hash));
        assertFalse(hasher.verifyOtpHash("111111", hash));
        assertFalse(hasher.verifyOtpHash(null, hash));
        assertFalse(hasher.verifyOtpHash(code, null));
        assertFalse(hasher.verifyOtpHash(code, new byte[32]));
    }

    @Test
    @DisplayName("Hai khóa bí mật khác nhau cho hai hash khác nhau")
    void differentSecretsGiveDifferentHashes() {
        byte[] first = new OtpHasher("secret-one").hashOtp("123456");
        byte[] second = new OtpHasher("secret-two").hashOtp("123456");
        assertFalse(java.util.Arrays.equals(first, second));
    }
}
