package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

public record OrganizationMemberDto(
        UUID membershipId,
        UUID userId,
        String userName,
        String fullName,
        String email,
        OrganizationRole role,
        boolean isActive,
        Instant joinedAt) {
    public String toJson() {
        return object(
                field("membershipId", text(membershipId)),
                field("userId", text(userId)),
                field("userName", nullableText(userName)),
                field("fullName", nullableText(fullName)),
                field("email", nullableText(email)),
                field("role", text(role)),
                field("isActive", number(isActive)),
                field("joinedAt", nullableText(joinedAt)));
    }
}
