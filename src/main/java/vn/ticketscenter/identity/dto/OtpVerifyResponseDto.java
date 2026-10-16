package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;

public record OtpVerifyResponseDto(boolean success, String message, String resetToken) {
    public static OtpVerifyResponseDto forEmailVerification(String message) {
        return new OtpVerifyResponseDto(true, message, null);
    }

    public static OtpVerifyResponseDto forPasswordReset(String message, String resetToken) {
        return new OtpVerifyResponseDto(true, message, resetToken);
    }

    public String toJson() {
        StringBuilder json =
                new StringBuilder("{\"success\":")
                        .append(success)
                        .append(",\"message\":")
                        .append(HttpResponses.jsonString(message));
        if (resetToken != null) {
            json.append(",\"resetToken\":").append(HttpResponses.jsonString(resetToken));
        }
        return json.append('}').toString();
    }
}
