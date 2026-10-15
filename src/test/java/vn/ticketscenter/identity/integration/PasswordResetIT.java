package vn.ticketscenter.identity.integration;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.OtpHasher;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.ResetTokenCodec;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

public class PasswordResetIT {

    @Test
    @DisplayName("Kiểm thử tích hợp gửi mã OTP thực tế qua Gmail SMTP về hộp thư người dùng")
    void testSendRealOtpEmailToUser() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not ready");
            }
            TransactionManager tm = registry.transactionManager();
            UserRepository userRepository = new UserRepository();
            OtpRepository otpRepository = new OtpRepository();
            OtpHasher otpHasher = new OtpHasher();
            ResetTokenCodec resetTokens = new ResetTokenCodec();
            SmtpEmailSender smtpEmailSender = new SmtpEmailSender();

            OtpService otpService = new OtpService(tm, otpRepository, userRepository, otpHasher, resetTokens, smtpEmailSender);

            String targetEmail = "recipient@fixture.invalid";

            // Đảm bảo user tồn tại trong hệ thống
            tm.execute(DatabasePrincipal.AUTH, em -> {
                if (userRepository.findByNormalizedEmail(em, targetEmail).isEmpty()) {
                    User user = new User(targetEmail, "devonxjz", "Trần Lê Thái",
                            new PasswordHasher().hash("SecureTestPass123!"));
                    userRepository.save(em, user);
                }
                // Xóa các OTP cũ của email test để reset cooldown và làm sạch trạng thái
                em.createQuery("DELETE FROM Otp o WHERE LOWER(o.emailNormalized) = :email")
                        .setParameter("email", targetEmail)
                        .executeUpdate();
                return null;
            });

            // Gửi OTP thực tế qua mạng về mail của user
            boolean sent = otpService.sendOtp(new OtpSendRequestDto(targetEmail, OtpPurpose.RESET_PASSWORD));
            assertTrue(sent, "Gửi mã OTP qua SMTP phải thành công");

            // Kiểm tra OTP đã được tạo và lưu hợp lệ trong database
            Otp otp = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestValidOtpByEmail(em, targetEmail, OtpPurpose.RESET_PASSWORD, Instant.now()).orElse(null));
            assertNotNull(otp, "Bản ghi OTP phải được lưu trữ trong database tc_otps");
            assertNotNull(otp.getSecretHash(), "Secret hash không được rỗng");
            assertFalse(otp.isConsumed(), "Mã OTP mới không được ở trạng thái consumed");

            System.out.println("==================================================================");
            System.out.println(" THÀNH CÔNG: Đã gửi mã OTP thực tế qua Gmail SMTP về hộp thư:");
            System.out.println("   --> " + targetEmail);
            System.out.println("==================================================================");
        } finally {
            registry.close();
        }
    }
}
