package vn.ticketscenter.identity.dto;

public record ForgotPasswordRequestDto(
        String email
) {
    public ForgotPasswordRequestDto {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email không được để trống");
        }
    }
}
