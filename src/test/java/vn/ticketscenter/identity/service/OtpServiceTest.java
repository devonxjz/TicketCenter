package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.integration.mail.FakeEmailSender;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
public class OtpServiceTest {

    private OtpRepository otpRepository;
    private UserRepository userRepository;
    private OtpCryptoHelper otpCryptoHelper;
    private FakeEmailSender emailSender;

    @BeforeEach
    void setUp() {
        otpRepository = new OtpRepository();
        userRepository = new UserRepository();
        otpCryptoHelper = new OtpCryptoHelper("secret-key-for-test-32-chars-long-123");
        emailSender = new FakeEmailSender();
    }

    private String extractOtpFromEmail(String body) {
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Không tìm thấy mã OTP 6 chữ số trong email body: " + body);
    }

    @Test
    @DisplayName("Send OTP: Gửi thành công lần đầu và gửi email chứa mã 6 chữ số")
    void testSendOtpSuccess() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "send." + suffix + "@ticketscenter.vn";

            // Tạo user trước
            tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash");
                userRepository.save(em, user);
                return null;
            });

            // Gửi OTP
            boolean sent = otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
            assertTrue(sent);

            // Kiểm tra email đã gửi
            assertEquals(1, emailSender.getSentMessages().size());
            assertEquals(email, emailSender.getSentMessages().get(0).recipient());
            String body = emailSender.getSentMessages().get(0).bodyText();
            String code = extractOtpFromEmail(body);
            assertEquals(6, code.length());

            // Kiểm tra DB có bản ghi OTP
            Otp otp = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestValidOtpByEmail(em, email, OtpPurpose.VERIFY_EMAIL, Instant.now()).orElse(null));
            assertNotNull(otp);
            assertFalse(otp.isConsumed());
            assertTrue(otpCryptoHelper.verifyOtpHash(code, otp.getSecretHash()));

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Send OTP: Cooldown 60s chặn gửi liên tiếp")
    void testSendOtpCooldown() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "cooldown." + suffix + "@ticketscenter.vn";

            tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash");
                userRepository.save(em, user);
                return null;
            });

            // Lần 1: Thành công
            assertTrue(otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL)));

            // Lần 2 ngay lập tức (<60s): Phải ném IllegalStateException("COOLDOWN_ACTIVE")
            IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                    otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL)));
            assertTrue(ex.getMessage().contains("COOLDOWN_ACTIVE"));

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Verify OTP: Xác minh VERIFY_EMAIL thành công cập nhật emailVerifiedAt của User")
    void testVerifyEmailOtpSuccess() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "verify." + suffix + "@ticketscenter.vn";

            User createdUser = tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash");
                userRepository.save(em, user);
                return user;
            });

            // Gửi OTP
            otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
            String code = extractOtpFromEmail(emailSender.getSentMessages().get(0).bodyText());

            // Xác minh OTP
            OtpVerifyResponseDto response = otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.VERIFY_EMAIL, code));
            assertTrue(response.success());
            assertNull(response.resetToken(), "VERIFY_EMAIL không trả về resetToken");

            // Kiểm tra User trong DB đã được cập nhật emailVerifiedAt
            User updatedUser = tm.execute(DatabasePrincipal.AUTH, em ->
                    userRepository.findById(em, createdUser.getId()).orElse(null));
            assertNotNull(updatedUser);
            assertTrue(updatedUser.isEmailVerified());

            // Thử xác minh lại cùng OTP -> Phải bị từ chối vì đã consumed
            assertThrows(IllegalStateException.class, () ->
                    otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.VERIFY_EMAIL, code)));

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Verify OTP: Xác minh RESET_PASSWORD thành công phát hành stateless resetToken")
    void testVerifyPasswordResetOtpSuccess() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "reset." + suffix + "@ticketscenter.vn";

            User createdUser = tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash123");
                userRepository.save(em, user);
                return user;
            });

            otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.RESET_PASSWORD));
            String code = extractOtpFromEmail(emailSender.getSentMessages().get(0).bodyText());

            OtpVerifyResponseDto response = otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.RESET_PASSWORD, code));
            assertTrue(response.success());
            assertNotNull(response.resetToken());

            // Token có thể parse và verify bằng OtpCryptoHelper
            OtpCryptoHelper.ResetTokenClaims claims = otpCryptoHelper.parseAndVerifyResetToken(
                    response.resetToken(), createdUser.getId(), createdUser.getPasswordHash(), createdUser.getAuthVersion());
            assertEquals(createdUser.getId(), claims.userId());

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Verify OTP: Nhập sai mã tăng số lần thử, sai quá 5 lần tự động khóa")
    void testVerifyOtpWrongCodeAndLockAfter5Attempts() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "wrong." + suffix + "@ticketscenter.vn";

            tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash");
                userRepository.save(em, user);
                return null;
            });

            otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));

            // Thử sai 4 lần -> Ném IllegalArgumentException thông báo mã không chính xác
            for (int i = 1; i <= 4; i++) {
                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                        otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.VERIFY_EMAIL, "000000")));
                assertTrue(ex.getMessage().contains("OTP_INVALID"));
            }

            // Thử sai lần thứ 5 -> Khóa và ném OTP_MAX_ATTEMPTS_EXCEEDED
            IllegalStateException lockEx = assertThrows(IllegalStateException.class, () ->
                    otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.VERIFY_EMAIL, "000000")));
            assertTrue(lockEx.getMessage().contains("OTP_MAX_ATTEMPTS_EXCEEDED"));

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Verify OTP: Purpose Isolation - Mã VERIFY_EMAIL không thể dùng cho RESET_PASSWORD")
    void testOtpPurposeIsolation() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpCryptoHelper, emailSender);

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "isolate." + suffix + "@ticketscenter.vn";

            tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash");
                userRepository.save(em, user);
                return null;
            });

            // Gửi OTP với purpose VERIFY_EMAIL
            otpService.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
            String code = extractOtpFromEmail(emailSender.getSentMessages().get(0).bodyText());

            // Cố tình verify với purpose RESET_PASSWORD -> Bị từ chối
            assertThrows(IllegalStateException.class, () ->
                    otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.RESET_PASSWORD, code)));

        } finally {
            registry.close();
        }
    }
}
