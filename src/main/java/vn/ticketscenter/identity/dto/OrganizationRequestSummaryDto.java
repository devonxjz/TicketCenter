package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.optional;

public record OrganizationRequestSummaryDto(
        UUID organizationId,
        String name,
        String contactEmail,
        OrganizationStatus status,
        Instant submittedAt,
        String rejectionReason,
        String requesterEmail,
        String contactPhone,
        UUID requesterId,
        String requesterName,
        String description) {
    public OrganizationRequestSummaryDto(
            UUID organizationId,
            String name,
            String contactEmail,
            OrganizationStatus status,
            Instant submittedAt,
            String rejectionReason) {
        this(
                organizationId,
                name,
                contactEmail,
                status,
                submittedAt,
                rejectionReason,
                contactEmail,
                null,
                null,
                null,
                null);
    }

    public OrganizationRequestSummaryDto(
            UUID organizationId,
            String name,
            String contactEmail,
            OrganizationStatus status,
            Instant submittedAt,
            String rejectionReason,
            String requesterEmail) {
        this(
                organizationId,
                name,
                contactEmail,
                status,
                submittedAt,
                rejectionReason,
                requesterEmail,
                null,
                null,
                null,
                null);
    }

    public static OrganizationRequestSummaryDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        String reqEmail =
                organization.getRequester() != null
                        ? organization.getRequester().getEmail()
                        : organization.getContactEmail();
        UUID reqId =
                organization.getRequester() != null ? organization.getRequester().getId() : null;
        String reqName =
                organization.getRequester() != null
                        ? organization.getRequester().getFullName()
                        : null;
        return new OrganizationRequestSummaryDto(
                organization.getId(),
                organization.getName(),
                organization.getContactEmail(),
                organization.getStatus(),
                organization.getSubmittedAt(),
                organization.getRejectionReason(),
                reqEmail,
                organization.getContactPhone(),
                reqId,
                reqName,
                organization.getDescription());
    }

    public String eventTitle() {
        return OrganizationRequestDetailDto.parseTag(description, "Event");
    }

    public String eventDate() {
        return OrganizationRequestDetailDto.parseTag(description, "Date");
    }

    public String eventVenue() {
        return OrganizationRequestDetailDto.parseTag(description, "Venue");
    }

    public String toJson() {
        boolean hasRequester = requesterId != null || requesterName != null || requesterEmail != null;
        return object(
                field("organizationId", nullableText(organizationId)),
                field("name", nullableText(name)),
                field("contactEmail", nullableText(contactEmail)),
                field("contactPhone", nullableText(contactPhone)),
                field("requesterEmail", nullableText(requesterEmail)),
                hasRequester
                        ? field(
                                "requester",
                                object(
                                        field("userId", nullableText(requesterId)),
                                        field("fullName", nullableText(requesterName)),
                                        field("email", nullableText(requesterEmail))))
                        : null,
                field("status", nullableText(status)),
                field("submittedAt", nullableText(submittedAt)),
                field("rejectionReason", nullableText(rejectionReason)),
                optional("description", description),
                optional("eventTitle", eventTitle()),
                optional("eventDate", eventDate()),
                optional("eventVenue", eventVenue()));
    }
}
