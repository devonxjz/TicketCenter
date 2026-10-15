package vn.ticketscenter.identity.service;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.model.User;
import vn.ticketscenter.identity.repository.UserRepository;

import java.util.Objects;
import java.util.UUID;

public class PasswordResetService {

    public static final String NEUTRAL_FORGOT_PASSWORD_MESSAGE =
            "Nếu địa chỉ email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi qua email.";

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final OtpCryptoHelper otpCryptoHelper;
    private final PasswordHasher passwordHasher;
    private final UserSessionCache sessionCache;

    public PasswordResetService(TransactionManager transactions,
                                UserRepository userRepository,
                                OtpService otpService,
                                OtpCryptoHelper otpCryptoHelper,
                                PasswordHasher passwordHasher,
                                UserSessionCache sessionCache) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.otpService = Objects.requireNonNull(otpService, "otpService must not be null");
        this.otpCryptoHelper = Objects.requireNonNull(otpCryptoHelper, "otpCryptoHelper must not be null");
        this.passwordHasher = Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.sessionCache = Objects.requireNonNull(sessionCache, "sessionCache must not be null");
    }

    /**
     * Xử lý yêu cầu quên mật khẩu:
     * Gửi mã OTP xác thực qua email và luôn trả về thông điệp trung tính để chống User Enumeration.
     */
    public String forgotPassword(ForgotPasswordRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu yêu cầu không được để trống");
        }

        try {
            otpService.sendOtp(new OtpSendRequestDto(request.email(), OtpPurpose.RESET_PASSWORD));
        } catch (IdentityException failure) {
            // This endpoint has always reported a failed delivery as a plain OTP error (400), not a 500.
            if (failure.error() == IdentityError.EMAIL_SEND_FAILED) {
                throw new IdentityException(IdentityError.OTP_ERROR, failure.getMessage(), failure);
            }
            throw failure;
        }
        return NEUTRAL_FORGOT_PASSWORD_MESSAGE;
    }

    /**
     * Đặt lại mật khẩu bằng stateless resetToken:
     * Xác thực token với passwordHash và authVersion hiện tại.
     * Cập nhật password mới và tăng authVersion để tự động vô hiệu hóa token cũ và các session cũ.
     */
    public boolean resetPassword(PasswordResetRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đặt lại mật khẩu không được để trống");
        }

        UUID userId = otpCryptoHelper.extractUserIdFromToken(request.token());

        return Boolean.TRUE.equals(transactions.execute(DatabasePrincipal.AUTH, em -> {
            User user = userRepository.findById(em, userId).orElse(null);
            if (user == null) {
                throw new IdentityException(IdentityError.INVALID_TOKEN,
                        "INVALID_TOKEN: Token không hợp lệ hoặc tài khoản không tồn tại");
            }

            // Xác thực chữ ký token và tính hợp lệ với trạng thái hiện tại của tài khoản
            try {
                otpCryptoHelper.parseAndVerifyResetToken(
                        request.token(), user.getId(), user.getPasswordHash(), user.getAuthVersion());
            } catch (SecurityException invalid) {
                throw new IdentityException(IdentityError.INVALID_TOKEN, invalid.getMessage(), invalid);
            }

            // Băm mật khẩu mới (12-256 ký tự đã được validate tại DTO)
            String newPasswordHash = passwordHasher.hash(request.newPassword());

            // Cập nhật password mới và tăng authVersion trong DB
            userRepository.updatePasswordHashAndAuthVersion(em, user.getId(), newPasswordHash);

            // Vô hiệu hóa cache Redis của user
            sessionCache.evict(user.getId());

            return true;
        }));
    }
}
