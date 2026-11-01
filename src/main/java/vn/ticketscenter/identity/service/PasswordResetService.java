package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;

public interface PasswordResetService {
    String NEUTRAL_FORGOT_PASSWORD_MESSAGE =
            "Nếu địa chỉ email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi qua email.";

    /**
     * Handles a forgotten password:
     * emails an OTP and always answers with the same neutral message (no user enumeration).
     */
    String forgotPassword(ForgotPasswordRequestDto request);

    /**
     * Resets the password with a stateless resetToken:
     * the token is checked against the current passwordHash and authVersion,
     * then the new password is stored and authVersion raised, which voids old tokens and sessions.
     */
    boolean resetPassword(PasswordResetRequestDto request);
}
