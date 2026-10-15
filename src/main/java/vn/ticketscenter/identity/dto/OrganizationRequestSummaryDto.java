package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
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
        String requesterEmail,
        String contactPhone,
        UUID requesterId,
        String requesterName,
        String description
) {
    public OrganizationRequestSummaryDto(
            UUID organizationId,
            String name,
            String contactEmail,
            OrganizationStatus status,
            Instant submittedAt,
            String rejectionReason
    ) {
        this(organizationId, name, contactEmail, status, submittedAt, rejectionReason, contactEmail,
                null, null, null, null);
    }

    public OrganizationRequestSummaryDto(
            UUID organizationId,
            String name,
            String contactEmail,
            OrganizationStatus status,
            Instant submittedAt,
            String rejectionReason,
            String requesterEmail
    ) {
        this(organizationId, name, contactEmail, status, submittedAt, rejectionReason, requesterEmail,
                null, null, null, null);
    }

    public static OrganizationRequestSummaryDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        String reqEmail = organization.getRequester() != null
                ? organization.getRequester().getEmail()
                : organization.getContactEmail();
        UUID reqId = organization.getRequester() != null ? organization.getRequester().getId() : null;
        String reqName = organization.getRequester() != null ? organization.getRequester().getFullName() : null;
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
                organization.getDescription()
        );
    }

    public String eventTitle() { return OrganizationRequestDetailDto.parseTag(description, "Event"); }
    public String eventDate() { return OrganizationRequestDetailDto.parseTag(description, "Date"); }
    public String eventVenue() { return OrganizationRequestDetailDto.parseTag(description, "Venue"); }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"organizationId\":").append(json(organizationId))
          .append(",\"name\":").append(json(name))
          .append(",\"contactEmail\":").append(json(contactEmail))
          .append(",\"contactPhone\":").append(json(contactPhone))
          .append(",\"requesterEmail\":").append(json(requesterEmail));
        if (requesterId != null || requesterName != null || requesterEmail != null) {
            sb.append(",\"requester\":{")
              .append("\"userId\":").append(json(requesterId))
              .append(",\"fullName\":").append(json(requesterName))
              .append(",\"email\":").append(json(requesterEmail))
              .append("}");
        }
        sb.append(",\"status\":").append(json(status))
          .append(",\"submittedAt\":").append(json(submittedAt))
          .append(",\"rejectionReason\":").append(json(rejectionReason));
        if (description != null) {
            sb.append(",\"description\":").append(HttpResponses.jsonString(description));
        }
        appendIfPresent(sb, "eventTitle", eventTitle());
        appendIfPresent(sb, "eventDate", eventDate());
        appendIfPresent(sb, "eventVenue", eventVenue());
        return sb.append("}").toString();
    }

    private static void appendIfPresent(StringBuilder sb, String field, String value) {
        if (value != null) {
            sb.append(",\"").append(field).append("\":").append(HttpResponses.jsonString(value));
        }
    }

    private static String json(Object value) {
        return HttpResponses.jsonString(value == null ? null : value.toString());
    }
}
