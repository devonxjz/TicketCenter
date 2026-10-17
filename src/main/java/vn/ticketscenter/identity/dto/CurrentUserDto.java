package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.model.PlatformRole;

import java.util.UUID;

public record CurrentUserDto(
        UUID id,
        String email,
        String userName,
        String fullName,
        PlatformRole platformRole,
        boolean admin) {
    public String toJson() {
        return "{\"id\":"
                + HttpResponses.jsonString(String.valueOf(id))
                + ",\"email\":"
                + HttpResponses.jsonString(email)
                + ",\"userName\":"
                + HttpResponses.jsonString(userName)
                + ",\"fullName\":"
                + HttpResponses.jsonString(fullName)
                + ",\"platformRole\":"
                + HttpResponses.jsonString(String.valueOf(platformRole))
                + ",\"admin\":"
                + admin
                + "}";
    }
}
