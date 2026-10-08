package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.integration.mail.EmailDeliveryException;
import vn.ticketscenter.identity.integration.mail.EmailMessage;
import vn.ticketscenter.identity.integration.mail.EmailSender;
import vn.ticketscenter.identity.model.Otp;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OtpService {

    private static final Logger LOGGER = Logger.getLogger(OtpService.class.getName());
    private static final int COOLDOWN_SECONDS = 60;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final TransactionManager transactions;
    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final OtpCryptoHelper otpCryptoHelper;
    private final EmailSender emailSender;

    public OtpService(TransactionManager transactions,
                      OtpRepository otpRepository,
                      UserRepository userRepository,
                      OtpCryptoHelper otpCryptoHelper,
                      EmailSender emailSender) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.otpRepository = Objects.requireNonNull(otpRepository, "otpRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.otpCryptoHelper = Objects.requireNonNull(otpCryptoHelper, "otpCryptoHelper must not be null");
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender must not be null");
    }

    /**
     * Gửi mã OTP xác thực qua email (VERIFY_EMAIL hoặc RESET_PASSWORD).
     * Áp dụng Cooldown 60s và tự động vô hiệu hóa mã cũ trước đó.
     */
    public boolean sendOtp(OtpSendRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu gửi OTP không được để trống");
        }

        String email = request.email();
        String normalizedEmail = AccountService.normalizeEmail(email);
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@") || normalizedEmail.length() > 320) {
            throw new IllegalArgumentException("Email không đúng định dạng");
        }

        Instant now = Instant.now();

        EmailMessage messageToSend = transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findByNormalizedEmail(em, normalizedEmail).orElse(null);

            // Chống User Enumeration: Nếu RESET_PASSWORD mà user không tồn tại, thực hiện dummy hashing và trả về null
            if (user == null) {
                if (request.purpose() == OtpPurpose.RESET_PASSWORD) {
                    otpCryptoHelper.hashOtp("000000");
                    return null;
                }
                throw new IllegalArgumentException("USER_NOT_FOUND: Không tìm thấy tài khoản với email này");
            }

            // Kiểm tra Cooldown 60s
            Otp latestOtp = otpRepository.findLatestOtpByEmail(em, normalizedEmail, request.purpose()).orElse(null);
            if (latestOtp != null && latestOtp.getCreatedAt() != null) {
                long elapsedSeconds = Duration.between(latestOtp.getCreatedAt(), now).getSeconds();
                if (elapsedSeconds < COOLDOWN_SECONDS) {
                    long remainingSeconds = COOLDOWN_SECONDS - elapsedSeconds;
                    throw new IllegalStateException("COOLDOWN_ACTIVE: Vui lòng đợi " + remainingSeconds + " giây trước khi gửi lại mã OTP");
                }
            }

            // Vô hiệu hóa mọi mã OTP chưa tiêu thụ trước đó
            otpRepository.invalidateExistingOtpsByEmail(em, normalizedEmail, request.purpose(), now);

            // Sinh mã OTP 6 số ngẫu nhiên và băm HMAC-SHA256
            String rawOtpCode = otpCryptoHelper.generateOtpCode();
            byte[] secretHash = otpCryptoHelper.hashOtp(rawOtpCode);
            Instant expiresAt = now.plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);

            Otp newOtp = new Otp(user, normalizedEmail, request.purpose(), secretHash, expiresAt);
            otpRepository.save(em, newOtp);

            // Chuẩn bị nội dung email
            String subject = (request.purpose() == OtpPurpose.VERIFY_EMAIL)
                    ? "[TicketsCenter] Xác minh địa chỉ email"
                    : "[TicketsCenter] Yêu cầu đặt lại mật khẩu";

            String purposeAction = (request.purpose() == OtpPurpose.VERIFY_EMAIL)
                    ? "xác minh địa chỉ email của bạn"
                    : "đặt lại mật khẩu tài khoản của bạn";

            String bodyText = String.format(
                    "Xin chào %s,\n\n" +
                    "Mã xác thực OTP để %s là:\n\n" +
                    "    %s\n\n" +
                    "Mã này có hiệu lực trong vòng %d phút. Vì lý do bảo mật, tuyệt đối không chia sẻ mã này với bất kỳ ai.\n" +
                    "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.\n\n" +
                    "Trân trọng,\nĐội ngũ TicketsCenter",
                    user.getFullName(), purposeAction, rawOtpCode, OTP_EXPIRY_MINUTES
            );

            return new EmailMessage(email, subject, bodyText, null);
        });

        // Nếu user == null (RESET_PASSWORD dummy hash), message trả về null
        if (messageToSend == null) {
            return true;
        }

        // 2. Gửi email qua mạng ngoài transaction DB để không chiếm dụng connection pool
        try {
            emailSender.send(messageToSend);
        } catch (EmailDeliveryException ex) {
            LOGGER.log(Level.WARNING, "Không thể gửi email OTP tới " + email + ": " + ex.getMessage());
            // Vô hiệu hóa mã OTP vừa tạo trong một transaction ngắn
            transactions.execute(DatabasePrincipal.AUTH, em -> {
                otpRepository.invalidateExistingOtpsByEmail(em, normalizedEmail, request.purpose(), Instant.now());
                return null;
            });
            throw new IllegalStateException("EMAIL_SEND_FAILED: Không thể gửi email chứa mã OTP, vui lòng thử lại sau.", ex);
        }

        return true;
    }

    /**
     * Xác thực mã OTP.
     * Cập nhật email_verified_at cho VERIFY_EMAIL hoặc cấp stateless resetToken cho RESET_PASSWORD.
     * Kiểm tra số lần sai tối đa 5 lần và tiêu thụ nguyên tử (atomic consume).
     */
    public OtpVerifyResponseDto verifyOtp(OtpVerifyRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu xác minh OTP không được để trống");
        }

        String rawCode = request.code().trim();
        String normalizedEmail = AccountService.normalizeEmail(request.email());
        Instant now = Instant.now();

        // 1. Kiểm tra trạng thái hiện tại của User và OTP
        Otp currentOtp = transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findByNormalizedEmail(em, normalizedEmail).orElse(null);
            if (user == null) {
                throw new IllegalArgumentException("USER_NOT_FOUND: Tài khoản không tồn tại");
            }
            return otpRepository.findLatestOtpByEmail(em, normalizedEmail, request.purpose()).orElse(null);
        });

        if (currentOtp == null) {
            throw new IllegalStateException("OTP_NOT_FOUND: Không tìm thấy mã OTP hợp lệ cho yêu cầu này");
        }

        if (currentOtp.isConsumed()) {
            throw new IllegalStateException("OTP_ALREADY_USED: Mã OTP này đã được sử dụng");
        }

        if (currentOtp.isInvalidated() || currentOtp.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new IllegalStateException("OTP_MAX_ATTEMPTS_EXCEEDED: Mã OTP đã bị khóa do nhập sai quá " + MAX_FAILED_ATTEMPTS + " lần");
        }

        if (currentOtp.isExpired(now)) {
            throw new IllegalStateException("OTP_EXPIRED: Mã OTP đã hết hạn (hiệu lực " + OTP_EXPIRY_MINUTES + " phút)");
        }

        // 2. So sánh mã băm an toàn constant-time
        boolean matches = otpCryptoHelper.verifyOtpHash(rawCode, currentOtp.getSecretHash());
        if (!matches) {
            // Commit việc tăng failed_attempts vào DB để không bị rollback
            int newAttempts = transactions.execute(DatabasePrincipal.AUTH, em -> {
                otpRepository.incrementFailedAttempts(em, currentOtp.getId(), now);
                return otpRepository.findById(em, currentOtp.getId())
                        .map(Otp::getFailedAttempts)
                        .orElse(currentOtp.getFailedAttempts() + 1);
            });

            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                throw new IllegalStateException("OTP_MAX_ATTEMPTS_EXCEEDED: Bạn đã nhập sai mã OTP quá " + MAX_FAILED_ATTEMPTS + " lần. Mã đã bị khóa.");
            }
            int remaining = MAX_FAILED_ATTEMPTS - newAttempts;
            throw new IllegalArgumentException("OTP_INVALID: Mã OTP không chính xác. Bạn còn " + remaining + " lần thử.");
        }

        // 3. Mã đúng: Tiêu thụ nguyên tử và thực hiện hành động tương ứng trong một transaction
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findByNormalizedEmail(em, normalizedEmail)
                    .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND: Tài khoản không tồn tại"));

            boolean consumed = otpRepository.consumeOtpAtomically(em, currentOtp.getId(), now);
            if (!consumed) {
                throw new IllegalStateException("OTP_ALREADY_USED: Mã OTP đã được xác thực bởi một yêu cầu khác");
            }

            if (request.purpose() == OtpPurpose.VERIFY_EMAIL) {
                userRepository.updateEmailVerifiedAt(em, user.getId(), now);
                return OtpVerifyResponseDto.forEmailVerification("Xác minh email thành công");
            } else if (request.purpose() == OtpPurpose.RESET_PASSWORD) {
                Instant tokenExpiresAt = now.plus(RESET_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);
                String resetToken = otpCryptoHelper.generateResetToken(
                        user.getId(), tokenExpiresAt, user.getPasswordHash(), user.getAuthVersion());
                return OtpVerifyResponseDto.forPasswordReset("Xác minh OTP thành công", resetToken);
            } else {
                throw new IllegalArgumentException("Mục đích OTP không được hỗ trợ");
            }
        });
    }
}
