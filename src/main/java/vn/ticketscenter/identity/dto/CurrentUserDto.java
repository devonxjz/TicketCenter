package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.PlatformRole;

import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

public record CurrentUserDto(
        UUID id,
        String email,
        String userName,
        String fullName,
        PlatformRole platformRole,
        boolean admin) {
    public String toJson() {
        return object(
                field("id", text(String.valueOf(id))),
                field("email", nullableText(email)),
                field("userName", nullableText(userName)),
                field("fullName", nullableText(fullName)),
                field("platformRole", text(String.valueOf(platformRole))),
                field("admin", number(admin)));
    }
}
