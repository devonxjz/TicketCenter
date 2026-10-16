package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.time.Instant;
import java.util.UUID;

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
        return "{\"membershipId\":"
                + HttpResponses.jsonString(membershipId != null ? membershipId.toString() : "")
                + ",\"userId\":"
                + HttpResponses.jsonString(userId != null ? userId.toString() : "")
                + ",\"userName\":"
                + HttpResponses.jsonString(userName)
                + ",\"fullName\":"
                + HttpResponses.jsonString(fullName)
                + ",\"email\":"
                + HttpResponses.jsonString(email)
                + ",\"role\":"
                + HttpResponses.jsonString(role != null ? role.name() : "")
                + ",\"isActive\":"
                + isActive
                + ",\"joinedAt\":"
                + (joinedAt != null ? HttpResponses.jsonString(joinedAt.toString()) : "null")
                + "}";
    }
}
