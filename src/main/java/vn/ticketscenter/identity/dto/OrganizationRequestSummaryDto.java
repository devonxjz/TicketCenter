package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

public record OrganizationRequestSummaryDto(
        UUID organizationId,
        String name,
        String contactEmail,
        OrganizationStatus status,
        Instant submittedAt,
        String rejectionReason,
        String requesterEmail
) {
    public OrganizationRequestSummaryDto(
            UUID organizationId,
            String name,
            String contactEmail,
            OrganizationStatus status,
            Instant submittedAt,
            String rejectionReason
    ) {
        this(organizationId, name, contactEmail, status, submittedAt, rejectionReason, contactEmail);
    }

    public static OrganizationRequestSummaryDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        String reqEmail = organization.getRequester() != null ? organization.getRequester().getEmail() : organization.getContactEmail();
        return new OrganizationRequestSummaryDto(
                organization.getId(),
                organization.getName(),
                organization.getContactEmail(),
                organization.getStatus(),
                organization.getSubmittedAt(),
                organization.getRejectionReason(),
                reqEmail
        );
    }

    public String toJson() {
        return "{"
                + "\"organizationId\":" + (organizationId != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(organizationId.toString()) : "null")
                + ",\"name\":" + (name != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(name) : "null")
                + ",\"contactEmail\":" + (contactEmail != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(contactEmail) : "null")
                + ",\"requesterEmail\":" + (requesterEmail != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(requesterEmail) : "null")
                + ",\"status\":" + (status != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(status.name()) : "null")
                + ",\"submittedAt\":" + (submittedAt != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(submittedAt.toString()) : "null")
                + ",\"rejectionReason\":" + (rejectionReason != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(rejectionReason) : "null")
                + "}";
    }
}
