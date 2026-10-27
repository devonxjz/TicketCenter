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
import vn.ticketscenter.identity.dao.UserRepository;

import java.util.Objects;
import java.util.UUID;

public class PasswordResetServiceImpl implements PasswordResetService {

    private final TransactionManager transactions;
    private final UserRepository userRepository;
    private final OtpService otpService;
    private final ResetTokenCodec resetTokens;
    private final PasswordHasher passwordHasher;
    private final UserSessionCache sessionCache;

    public PasswordResetServiceImpl(
            TransactionManager transactions,
            UserRepository userRepository,
            OtpService otpService,
            ResetTokenCodec resetTokens,
            PasswordHasher passwordHasher,
            UserSessionCache sessionCache) {
        this.transactions = Objects.requireNonNull(transactions, "transactions must not be null");
        this.userRepository =
                Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.otpService = Objects.requireNonNull(otpService, "otpService must not be null");
        this.resetTokens = Objects.requireNonNull(resetTokens, "resetTokens must not be null");
        this.passwordHasher =
                Objects.requireNonNull(passwordHasher, "passwordHasher must not be null");
        this.sessionCache = Objects.requireNonNull(sessionCache, "sessionCache must not be null");
    }

    @Override
    public String forgotPassword(ForgotPasswordRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu yêu cầu không được để trống");
        }

        try {
            otpService.sendOtp(new OtpSendRequestDto(request.email(), OtpPurpose.RESET_PASSWORD));
        } catch (IdentityException failure) {
            // This endpoint has always reported a failed delivery as a plain OTP error (400), not a
            // 500.
            if (failure.error() == IdentityError.EMAIL_SEND_FAILED) {
                throw new IdentityException(IdentityError.OTP_ERROR, failure.getMessage(), failure);
            }
            throw failure;
        }
        return NEUTRAL_FORGOT_PASSWORD_MESSAGE;
    }

    @Override
    public boolean resetPassword(PasswordResetRequestDto request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đặt lại mật khẩu không được để trống");
        }

        UUID userId = resetTokens.extractUserId(request.token());

        return Boolean.TRUE.equals(
                transactions.execute(
                        DatabasePrincipal.AUTH,
                        em -> {
                            User user = userRepository.findById(em, userId).orElse(null);
                            if (user == null) {
                                throw new IdentityException(
                                        IdentityError.INVALID_TOKEN,
                                        "INVALID_TOKEN: Token không hợp lệ hoặc tài khoản không tồn tại");
                            }

                            // Xác thực chữ ký token và tính hợp lệ với trạng thái hiện tại của tài
                            // khoản
                            resetTokens.verify(
                                    request.token(),
                                    user.getId(),
                                    user.getPasswordHash(),
                                    user.getAuthVersion());

                            // Băm mật khẩu mới (12-256 ký tự đã được validate tại DTO)
                            String newPasswordHash = passwordHasher.hash(request.newPassword());

                            // Cập nhật password mới và tăng authVersion trong DB
                            userRepository.updatePasswordHashAndAuthVersion(
                                    em, user.getId(), newPasswordHash);

                            // Vô hiệu hóa cache Redis của user
                            sessionCache.evict(user.getId());

                            return true;
                        }));
    }
}
