package vn.ticketscenter.identity.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;
import vn.ticketscenter.identity.integration.mail.FakeEmailSender;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.dao.OtpRepository;
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
public class PasswordResetServiceTest {

    private OtpRepository otpRepository;
    private UserRepository userRepository;
    private OtpHasher otpHasher;
    private ResetTokenCodec resetTokens;
    private PasswordHasher passwordHasher;
    private FakeEmailSender emailSender;

    @BeforeEach
    void setUp() {
        otpRepository = new OtpRepository();
        userRepository = new UserRepository();
        otpHasher = new OtpHasher("secret-key-password-reset-test-32chars");
        resetTokens = new ResetTokenCodec("secret-key-password-reset-test-32chars");
        passwordHasher = new PasswordHasher();
        emailSender = new FakeEmailSender();
    }

    private String extractOtpFromEmail(String body) {
        Pattern pattern = Pattern.compile("\\b(\\d{6})\\b");
        Matcher matcher = pattern.matcher(body);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Không tìm thấy mã OTP trong email body: " + body);
    }

    @Test
    @DisplayName("forgotPassword: Phản hồi thông điệp trung tính cho cả email tồn tại và không tồn tại")
    void testForgotPasswordNeutralMessage() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpServiceImpl(tm, otpRepository, userRepository, otpHasher, resetTokens, emailSender);
            PasswordResetService resetService = new PasswordResetServiceImpl(tm, userRepository, otpService, resetTokens, passwordHasher, UserSessionCache.disabled());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String existingEmail = "existing." + suffix + "@ticketscenter.vn";
            String nonExistingEmail = "unknown." + suffix + "@ticketscenter.vn";

            tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(existingEmail, "user" + suffix, "Test User", passwordHasher.hash("OldPassword123!"));
                userRepository.save(em, user);
                return null;
            });

            // 1. Email tồn tại
            String msg1 = resetService.forgotPassword(new ForgotPasswordRequestDto(existingEmail));
            assertNotNull(msg1);
            assertEquals(1, emailSender.getSentMessages().size());

            // 2. Email không tồn tại
            String msg2 = resetService.forgotPassword(new ForgotPasswordRequestDto(nonExistingEmail));
            assertNotNull(msg2);

            // Hai thông điệp trả về cho người dùng phải giống nhau hoàn toàn để chống user enumeration
            assertEquals(msg1, msg2);

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("resetPassword: Đổi mật khẩu thành công và tự động vô hiệu hóa token cũ")
    void testResetPasswordSuccessAndTokenRevocation() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            OtpService otpService = new OtpServiceImpl(tm, otpRepository, userRepository, otpHasher, resetTokens, emailSender);
            PasswordResetService resetService = new PasswordResetServiceImpl(tm, userRepository, otpService, resetTokens, passwordHasher, UserSessionCache.disabled());

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "resetpwd." + suffix + "@ticketscenter.vn";
            String oldPassword = "OldSecurePassword123!";
            String newPassword = "BrandNewPassword2026!";

            User user = tm.execute(DatabasePrincipal.AUTH, em -> {
                User u = new User(email, "user" + suffix, "Test User", passwordHasher.hash(oldPassword));
                userRepository.save(em, u);
                return u;
            });

            // 1. Gửi OTP quên mật khẩu
            resetService.forgotPassword(new ForgotPasswordRequestDto(email));
            String code = extractOtpFromEmail(emailSender.getSentMessages().get(0).bodyText());

            // 2. Xác minh OTP để nhận resetToken
            OtpVerifyResponseDto verifyRes = otpService.verifyOtp(new OtpVerifyRequestDto(email, OtpPurpose.RESET_PASSWORD, code));
            String resetToken = verifyRes.resetToken();
            assertNotNull(resetToken);

            // 3. Đổi mật khẩu lần đầu -> Thành công
            boolean resetOk = resetService.resetPassword(new PasswordResetRequestDto(resetToken, newPassword));
            assertTrue(resetOk);

            // Kiểm tra DB: passwordHash đã đổi và authVersion đã tăng
            User updatedUser = tm.execute(DatabasePrincipal.AUTH, em ->
                    userRepository.findById(em, user.getId()).orElse(null));
            assertNotNull(updatedUser);
            assertTrue(passwordHasher.verify(newPassword, updatedUser.getPasswordHash()));
            assertFalse(passwordHasher.verify(oldPassword, updatedUser.getPasswordHash()));
            assertEquals(1, updatedUser.getAuthVersion());

            // 4. Thử tái sử dụng resetToken cũ -> Bị từ chối ngay lập tức do authVersion và passwordHash đã đổi
            IdentityException stale = assertThrows(IdentityException.class, () ->
                    resetService.resetPassword(new PasswordResetRequestDto(resetToken, "AnotherPassword123!")));
            assertEquals(IdentityError.INVALID_TOKEN, stale.error());

        } finally {
            registry.close();
        }
    }
}
