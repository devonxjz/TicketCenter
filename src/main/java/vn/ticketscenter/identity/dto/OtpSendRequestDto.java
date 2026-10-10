package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OtpPurpose;

public record OtpSendRequestDto(
        String email,
        OtpPurpose purpose
) {
    public OtpSendRequestDto {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("Mục đích OTP không được để trống");
        }
    }
}
