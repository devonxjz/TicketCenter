package vn.ticketscenter.identity.dto;

import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

public record ProfileDto(UUID id, String email, String userName, String fullName, String phone) {

    public String toJson() {
        return object(
                field("id", text(String.valueOf(id))),
                field("email", nullableText(email)),
                field("userName", nullableText(userName)),
                field("fullName", nullableText(fullName)),
                field("phone", nullableText(phone)));
    }
}
