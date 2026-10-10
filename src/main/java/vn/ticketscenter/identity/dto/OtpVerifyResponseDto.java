package vn.ticketscenter.identity.dto;

public record OtpVerifyResponseDto(
        boolean success,
        String message,
        String resetToken
) {
    public static OtpVerifyResponseDto forEmailVerification(String message) {
        return new OtpVerifyResponseDto(true, message, null);
    }

    public static OtpVerifyResponseDto forPasswordReset(String message, String resetToken) {
        return new OtpVerifyResponseDto(true, message, resetToken);
    }
}
