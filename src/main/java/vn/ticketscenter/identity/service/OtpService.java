package vn.ticketscenter.identity.service;

import jakarta.persistence.EntityManager;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.integration.mail.EmailDeliveryException;
import vn.ticketscenter.identity.integration.mail.EmailMessage;
import vn.ticketscenter.identity.integration.mail.EmailSender;
import vn.ticketscenter.identity.integration.mail.OtpEmailComposer;
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

/** Orchestrates sending and verifying one-time codes; hashing, tokens and email text live in their own classes. */
public class OtpService {

    private static final Logger LOGGER = Logger.getLogger(OtpService.class.getName());
    private static final int COOLDOWN_SECONDS = 60;
    private static final int OTP_EXPIRY_MINUTES = 5;
    private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final TransactionManager transactions;
    private final OtpRepository otpRepository;
    private final UserRepository userRepository;
    private final OtpHasher otpHasher;
    private final ResetTokenCodec resetTokens;
    private final EmailSender emailSender;

    public OtpService(TransactionManager transactions,
                      OtpRepository otpRepository,
                      UserRepository userRepository,
                      OtpHasher otpHasher,
                      ResetTokenCodec resetTokens,
                      EmailSender emailSender) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.otpRepository = Objects.requireNonNull(otpRepository, "otpRepository must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.otpHasher = Objects.requireNonNull(otpHasher, "otpHasher must not be null");
        this.resetTokens = Objects.requireNonNull(resetTokens, "resetTokens must not be null");
        this.emailSender = Objects.requireNonNull(emailSender, "emailSender must not be null");
    }

    /**
     * Sends a VERIFY_EMAIL or RESET_PASSWORD code: validate, check the 60 s cooldown, invalidate older codes,
     * store the new one and mail it after the transaction has committed.
     */
    public boolean sendOtp(OtpSendRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Yêu cầu gửi OTP không được để trống");
        }
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        IdentityValidator.requireEmail(normalizedEmail);

        Instant now = Instant.now();
        EmailMessage message = transactions.execute(DatabasePrincipal.AUTH,
                em -> issueOtp(em, request, normalizedEmail, now));

        // RESET_PASSWORD for an unknown account reports success without sending, to avoid account enumeration.
        if (message != null) {
            deliver(message, request, normalizedEmail);
        }
        return true;
    }

    private EmailMessage issueOtp(EntityManager em, OtpSendRequestDto request, String normalizedEmail, Instant now) {
        User user = userRepository.findByNormalizedEmail(em, normalizedEmail).orElse(null);
        if (user == null) {
            return rejectUnknownAccount(request.purpose());
        }
        requireCooldownElapsed(em, normalizedEmail, request.purpose(), now);
        otpRepository.invalidateExistingOtpsByEmail(em, normalizedEmail, request.purpose(), now);

        String code = otpHasher.generateOtpCode();
        Instant expiresAt = now.plus(OTP_EXPIRY_MINUTES, ChronoUnit.MINUTES);
        otpRepository.save(em, new Otp(user, normalizedEmail, request.purpose(), otpHasher.hashOtp(code), expiresAt));

        return OtpEmailComposer.compose(request.email(), request.purpose(), user.getFullName(), code,
                OTP_EXPIRY_MINUTES);
    }

    /** Burns the time a hash would take for RESET_PASSWORD (returning no mail); other purposes are an error. */
    private EmailMessage rejectUnknownAccount(OtpPurpose purpose) {
        if (purpose == OtpPurpose.RESET_PASSWORD) {
            otpHasher.hashOtp("000000");
            return null;
        }
        throw new IdentityException(IdentityError.VALIDATION_FAILED,
                "USER_NOT_FOUND: Không tìm thấy tài khoản với email này");
    }

    private void requireCooldownElapsed(EntityManager em, String normalizedEmail, OtpPurpose purpose, Instant now) {
        Otp latest = otpRepository.findLatestOtpByEmail(em, normalizedEmail, purpose).orElse(null);
        if (latest == null || latest.getCreatedAt() == null) {
            return;
        }
        long elapsedSeconds = Duration.between(latest.getCreatedAt(), now).getSeconds();
        if (elapsedSeconds < COOLDOWN_SECONDS) {
            throw new IdentityException(IdentityError.COOLDOWN_ACTIVE, "COOLDOWN_ACTIVE: Vui lòng đợi "
                    + (COOLDOWN_SECONDS - elapsedSeconds) + " giây trước khi gửi lại mã OTP");
        }
    }

    /** Mails outside any transaction; when delivery fails the freshly stored code is invalidated. */
    private void deliver(EmailMessage message, OtpSendRequestDto request, String normalizedEmail) {
        try {
            emailSender.send(message);
        } catch (EmailDeliveryException failure) {
            LOGGER.log(Level.WARNING, "Không thể gửi email OTP tới " + request.email() + ": " + failure.getMessage());
            transactions.execute(DatabasePrincipal.AUTH, em -> {
                otpRepository.invalidateExistingOtpsByEmail(em, normalizedEmail, request.purpose(), Instant.now());
                return null;
            });
            throw new IdentityException(IdentityError.EMAIL_SEND_FAILED,
                    "EMAIL_SEND_FAILED: Không thể gửi email chứa mã OTP, vui lòng thử lại sau.", failure);
        }
    }

    /**
     * Verifies a code. A wrong code counts towards the five-attempt limit; a right one is consumed atomically
     * and then either marks the email verified or yields a password-reset token.
     */
    public OtpVerifyResponseDto verifyOtp(OtpVerifyRequestDto request) {
        if (request == null) {
            throw new IdentityException(IdentityError.OTP_INVALID, "Yêu cầu xác minh OTP không được để trống");
        }
        String rawCode = request.code().trim();
        String normalizedEmail = EmailNormalizer.normalize(request.email());
        Instant now = Instant.now();

        Otp current = transactions.execute(DatabasePrincipal.AUTH, em -> {
            requireUser(em, normalizedEmail);
            return otpRepository.findLatestOtpByEmail(em, normalizedEmail, request.purpose()).orElse(null);
        });
        requireUsable(current, now);

        if (!otpHasher.verifyOtpHash(rawCode, current.getSecretHash())) {
            throw recordFailedAttempt(current, now);
        }
        return consumeAndComplete(request, normalizedEmail, current, now);
    }

    private User requireUser(EntityManager em, String normalizedEmail) {
        return userRepository.findByNormalizedEmail(em, normalizedEmail).orElseThrow(() ->
                new IdentityException(IdentityError.OTP_INVALID, "USER_NOT_FOUND: Tài khoản không tồn tại"));
    }

    private void requireUsable(Otp otp, Instant now) {
        if (otp == null) {
            throw new IdentityException(IdentityError.OTP_NOT_FOUND,
                    "OTP_NOT_FOUND: Không tìm thấy mã OTP hợp lệ cho yêu cầu này");
        }
        if (otp.isConsumed()) {
            throw new IdentityException(IdentityError.OTP_ALREADY_USED, "OTP_ALREADY_USED: Mã OTP này đã được sử dụng");
        }
        if (otp.isInvalidated() || otp.getFailedAttempts() >= MAX_FAILED_ATTEMPTS) {
            throw new IdentityException(IdentityError.OTP_MAX_ATTEMPTS_EXCEEDED,
                    "OTP_MAX_ATTEMPTS_EXCEEDED: Mã OTP đã bị khóa do nhập sai quá " + MAX_FAILED_ATTEMPTS + " lần");
        }
        if (otp.isExpired(now)) {
            throw new IdentityException(IdentityError.OTP_EXPIRED,
                    "OTP_EXPIRED: Mã OTP đã hết hạn (hiệu lực " + OTP_EXPIRY_MINUTES + " phút)");
        }
    }

    /** Commits the extra failed attempt in its own transaction so the failure response cannot roll it back. */
    private IdentityException recordFailedAttempt(Otp otp, Instant now) {
        int attempts = transactions.execute(DatabasePrincipal.AUTH, em -> {
            otpRepository.incrementFailedAttempts(em, otp.getId(), now);
            return otpRepository.findById(em, otp.getId())
                    .map(Otp::getFailedAttempts)
                    .orElse(otp.getFailedAttempts() + 1);
        });
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            return new IdentityException(IdentityError.OTP_MAX_ATTEMPTS_EXCEEDED, "OTP_MAX_ATTEMPTS_EXCEEDED: "
                    + "Bạn đã nhập sai mã OTP quá " + MAX_FAILED_ATTEMPTS + " lần. Mã đã bị khóa.");
        }
        return new IdentityException(IdentityError.OTP_INVALID, "OTP_INVALID: Mã OTP không chính xác. Bạn còn "
                + (MAX_FAILED_ATTEMPTS - attempts) + " lần thử.");
    }

    private OtpVerifyResponseDto consumeAndComplete(OtpVerifyRequestDto request, String normalizedEmail,
                                                    Otp otp, Instant now) {
        return transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = requireUser(em, normalizedEmail);
            if (!otpRepository.consumeOtpAtomically(em, otp.getId(), now)) {
                throw new IdentityException(IdentityError.OTP_ALREADY_USED,
                        "OTP_ALREADY_USED: Mã OTP đã được xác thực bởi một yêu cầu khác");
            }
            return switch (request.purpose()) {
                case VERIFY_EMAIL -> {
                    userRepository.updateEmailVerifiedAt(em, user.getId(), now);
                    yield OtpVerifyResponseDto.forEmailVerification("Xác minh email thành công");
                }
                case RESET_PASSWORD -> {
                    Instant tokenExpiresAt = now.plus(RESET_TOKEN_EXPIRY_MINUTES, ChronoUnit.MINUTES);
                    String token = resetTokens.generate(
                            user.getId(), tokenExpiresAt, user.getPasswordHash(), user.getAuthVersion());
                    yield OtpVerifyResponseDto.forPasswordReset("Xác minh OTP thành công", token);
                }
            };
        });
    }
}
