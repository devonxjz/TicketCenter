package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.PlatformRole;

import java.util.UUID;

public record CurrentUserDto(
        UUID id,
        String email,
        String userName,
        String fullName,
        PlatformRole platformRole,
        boolean admin
) {
}
