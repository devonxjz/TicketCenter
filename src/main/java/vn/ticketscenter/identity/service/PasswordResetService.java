package vn.ticketscenter.identity.service;

import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;

public interface PasswordResetService {
    String NEUTRAL_FORGOT_PASSWORD_MESSAGE =
            "Nếu địa chỉ email tồn tại trong hệ thống, hướng dẫn đặt lại mật khẩu đã được gửi qua email.";

    /**
     * Xử lý yêu cầu quên mật khẩu:
     * Gửi mã OTP xác thực qua email và luôn trả về thông điệp trung tính để chống User Enumeration.
     */
    String forgotPassword(ForgotPasswordRequestDto request);

    /**
     * Đặt lại mật khẩu bằng stateless resetToken:
     * Xác thực token với passwordHash và authVersion hiện tại.
     * Cập nhật password mới và tăng authVersion để tự động vô hiệu hóa token cũ và các session cũ.
     */
    boolean resetPassword(PasswordResetRequestDto request);
}
