package vn.ticketscenter.identity.dto;

public record PasswordResetRequestDto(
        String token,
        String newPassword
) {
    public PasswordResetRequestDto {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token không được để trống");
        }
        if (newPassword == null || newPassword.length() < 12 || newPassword.length() > 256) {
            throw new IllegalArgumentException("Mật khẩu mới phải có độ dài từ 12 đến 256 ký tự");
        }
    }
}
