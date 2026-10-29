package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.util.UUID;

public record MembershipSummaryDto(
        UUID organizationId, String organizationName, OrganizationRole role, boolean isActive) {

    public String toJson() {
        return "{\"organizationId\":"
                + HttpResponses.jsonString(organizationId.toString())
                + ",\"organizationName\":"
                + HttpResponses.jsonString(organizationName)
                + ",\"role\":"
                + HttpResponses.jsonString(role.name())
                + ",\"isActive\":"
                + isActive
                + "}";
    }
}
