package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OtpPurpose;

public record OtpVerifyRequestDto(String email, OtpPurpose purpose, String code) {
    public OtpVerifyRequestDto {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
        if (purpose == null) {
            throw new IllegalArgumentException("Mục đích OTP không được để trống");
        }
        if (code == null || !code.trim().matches("^[0-9]{6}$")) {
            throw new IllegalArgumentException("Mã OTP phải gồm đúng 6 chữ số");
        }
    }
}
