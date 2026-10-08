package vn.ticketscenter.identity.dto;

public record RegisterRequestDto(
        String userName,
        String fullName,
        String email,
        String password
) {
    public RegisterRequestDto {
        if (userName != null) userName = userName.trim();
        if (fullName != null) fullName = fullName.trim();
        if (email != null) email = email.trim();
    }
}
