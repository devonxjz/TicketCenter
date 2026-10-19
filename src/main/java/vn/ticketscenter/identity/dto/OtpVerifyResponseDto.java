package vn.ticketscenter.identity.dto;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.optional;

public record OtpVerifyResponseDto(boolean success, String message, String resetToken) {
    public static OtpVerifyResponseDto forEmailVerification(String message) {
        return new OtpVerifyResponseDto(true, message, null);
    }

    public static OtpVerifyResponseDto forPasswordReset(String message, String resetToken) {
        return new OtpVerifyResponseDto(true, message, resetToken);
    }

    public String toJson() {
        return object(
                field("success", number(success)),
                field("message", nullableText(message)),
                optional("resetToken", resetToken));
    }
}
