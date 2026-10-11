package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

public record OrganizationRequestDetailDto(
        UUID organizationId,
        String name,
        String contactEmail,
        String contactPhone,
        String description,
        OrganizationStatus status,
        UUID requesterId,
        Instant submittedAt,
        String rejectionReason
) {
    public static OrganizationRequestDetailDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        UUID reqId = organization.getRequester() != null ? organization.getRequester().getId() : null;
        return new OrganizationRequestDetailDto(
                organization.getId(),
                organization.getName(),
                organization.getContactEmail(),
                organization.getContactPhone(),
                organization.getDescription(),
                organization.getStatus(),
                reqId,
                organization.getSubmittedAt(),
                organization.getRejectionReason()
        );
    }

    public String toJson() {
        return "{"
                + "\"organizationId\":" + (organizationId != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(organizationId.toString()) : "null")
                + ",\"name\":" + (name != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(name) : "null")
                + ",\"contactEmail\":" + (contactEmail != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(contactEmail) : "null")
                + ",\"contactPhone\":" + (contactPhone != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(contactPhone) : "null")
                + ",\"description\":" + (description != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(description) : "null")
                + ",\"status\":" + (status != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(status.name()) : "null")
                + ",\"requesterId\":" + (requesterId != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(requesterId.toString()) : "null")
                + ",\"submittedAt\":" + (submittedAt != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(submittedAt.toString()) : "null")
                + ",\"rejectionReason\":" + (rejectionReason != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(rejectionReason) : "null")
                + "}";
    }
}
