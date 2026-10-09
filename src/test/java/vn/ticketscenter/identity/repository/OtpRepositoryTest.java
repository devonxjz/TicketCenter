package vn.ticketscenter.identity.repository;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class OtpRepositoryTest {

    private final OtpRepository otpRepository = new OtpRepository();
    private final UserRepository userRepository = new UserRepository();

    @Test
    @DisplayName("Lưu OTP, kiểm tra truy vấn hợp lệ và atomic consume chống race condition")
    void testOtpSaveAndAtomicConsume() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not available for integration testing");
            }
            TransactionManager tm = registry.transactionManager();

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "otp." + suffix + "@ticketscenter.vn";
            String userName = "user" + suffix;
            byte[] secretHash = new byte[32];
            secretHash[0] = 0x42;

            User testUser = tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, userName, "Test User", "salt:hash123");
                userRepository.save(em, user);
                return user;
            });

            Instant now = Instant.now();
            Instant expiresAt = now.plus(5, ChronoUnit.MINUTES);

            // 1. Lưu OTP
            UUID otpId = tm.execute(DatabasePrincipal.AUTH, em -> {
                Otp otp = new Otp(testUser, email, OtpPurpose.VERIFY_EMAIL, secretHash, expiresAt);
                otpRepository.save(em, otp);
                return otp.getId();
            });
            assertNotNull(otpId);

            // 2. Tìm OTP mới nhất hợp lệ
            Otp found = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestValidOtp(em, testUser.getId(), OtpPurpose.VERIFY_EMAIL, Instant.now()).orElse(null));
            assertNotNull(found);
            assertEquals(otpId, found.getId());
            assertEquals(email, found.getEmailNormalized());
            assertFalse(found.isConsumed());

            // 3. Atomic consume lần 1 -> Thành công (trả về true)
            boolean consumedFirst = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.consumeOtpAtomically(em, otpId, Instant.now()));
            assertTrue(consumedFirst, "Lần consume đầu tiên phải thành công");

            // 4. Atomic consume lần 2 (giả lập request race condition) -> Thất bại (trả về false)
            boolean consumedSecond = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.consumeOtpAtomically(em, otpId, Instant.now()));
            assertFalse(consumedSecond, "Lần consume thứ hai phải thất bại do OTP đã bị consumed");

            // 5. Sau khi consume, findLatestValidOtp không còn trả về OTP này nữa
            Optional<Otp> validAfterConsume = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestValidOtp(em, testUser.getId(), OtpPurpose.VERIFY_EMAIL, Instant.now()));
            assertTrue(validAfterConsume.isEmpty(), "OTP đã consume không được xuất hiện trong findLatestValidOtp");

        } finally {
            registry.close();
        }
    }

    @Test
    @DisplayName("Tăng failed_attempts và tự động vô hiệu hóa (invalidated_at) khi sai 5 lần")
    void testIncrementFailedAttemptsAutoInvalidate() {
        PersistenceRegistry registry = new PersistenceRegistry();
        try {
            registry.initialize();
            if (!registry.isReady()) {
                Assumptions.assumeTrue(false, "Database is not available for integration testing");
            }
            TransactionManager tm = registry.transactionManager();

            String suffix = UUID.randomUUID().toString().substring(0, 8);
            String email = "fail." + suffix + "@ticketscenter.vn";
            byte[] secretHash = new byte[32];

            User testUser = tm.execute(DatabasePrincipal.AUTH, em -> {
                User user = new User(email, "user" + suffix, "Test User", "salt:hash123");
                userRepository.save(em, user);
                return user;
            });

            UUID otpId = tm.execute(DatabasePrincipal.AUTH, em -> {
                Otp otp = new Otp(testUser, email, OtpPurpose.RESET_PASSWORD, secretHash, Instant.now().plus(5, ChronoUnit.MINUTES));
                otpRepository.save(em, otp);
                return otp.getId();
            });

            // Tăng 4 lần sai
            for (int i = 1; i <= 4; i++) {
                int count = i;
                tm.execute(DatabasePrincipal.AUTH, em -> {
                    otpRepository.incrementFailedAttempts(em, otpId, Instant.now());
                    return null;
                });
                Otp current = tm.execute(DatabasePrincipal.AUTH, em ->
                        otpRepository.findLatestOtpByEmail(em, email, OtpPurpose.RESET_PASSWORD).orElse(null));
                assertNotNull(current);
                assertEquals(count, current.getFailedAttempts());
                assertNull(current.getInvalidatedAt(), "Chưa đủ 5 lần thì chưa được invalidate");
            }

            // Lần thứ 5 sai -> Tự động invalidated_at được ghi nhận
            tm.execute(DatabasePrincipal.AUTH, em -> {
                otpRepository.incrementFailedAttempts(em, otpId, Instant.now());
                return null;
            });

            Otp lockedOtp = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestOtpByEmail(em, email, OtpPurpose.RESET_PASSWORD).orElse(null));
            assertNotNull(lockedOtp);
            assertEquals(5, lockedOtp.getFailedAttempts());
            assertNotNull(lockedOtp.getInvalidatedAt(), "Lần thứ 5 phải tự động gắn invalidated_at");

            // findLatestValidOtp sẽ không tìm thấy OTP đã bị khóa
            Optional<Otp> validOpt = tm.execute(DatabasePrincipal.AUTH, em ->
                    otpRepository.findLatestValidOtp(em, testUser.getId(), OtpPurpose.RESET_PASSWORD, Instant.now()));
            assertTrue(validOpt.isEmpty(), "OTP đã bị khóa không được coi là hợp lệ");

        } finally {
            registry.close();
        }
    }
}
