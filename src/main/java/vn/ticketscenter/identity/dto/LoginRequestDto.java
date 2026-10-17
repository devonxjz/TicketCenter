package vn.ticketscenter.identity.dto;

public record LoginRequestDto(String identifier, String password) {
    public LoginRequestDto {
        if (identifier != null) identifier = identifier.trim();
    }
}
