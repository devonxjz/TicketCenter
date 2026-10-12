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
        this(organizationId, name, contactEmail, status, submittedAt, rejectionReason, contactEmail, null, null, null, null);
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
        this(organizationId, name, contactEmail, status, submittedAt, rejectionReason, requesterEmail, null, null, null, null);
    }

    public static OrganizationRequestSummaryDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        String reqEmail = organization.getRequester() != null ? organization.getRequester().getEmail() : organization.getContactEmail();
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
        sb.append("\"organizationId\":").append(organizationId != null ? HttpResponses.jsonString(organizationId.toString()) : "null")
          .append(",\"name\":").append(name != null ? HttpResponses.jsonString(name) : "null")
          .append(",\"contactEmail\":").append(contactEmail != null ? HttpResponses.jsonString(contactEmail) : "null")
          .append(",\"contactPhone\":").append(contactPhone != null ? HttpResponses.jsonString(contactPhone) : "null")
          .append(",\"requesterEmail\":").append(requesterEmail != null ? HttpResponses.jsonString(requesterEmail) : "null");
        if (requesterId != null || requesterName != null || requesterEmail != null) {
            sb.append(",\"requester\":{")
              .append("\"userId\":").append(requesterId != null ? HttpResponses.jsonString(requesterId.toString()) : "null")
              .append(",\"fullName\":").append(requesterName != null ? HttpResponses.jsonString(requesterName) : "null")
              .append(",\"email\":").append(requesterEmail != null ? HttpResponses.jsonString(requesterEmail) : "null")
              .append("}");
        }
        sb.append(",\"status\":").append(status != null ? HttpResponses.jsonString(status.name()) : "null")
          .append(",\"submittedAt\":").append(submittedAt != null ? HttpResponses.jsonString(submittedAt.toString()) : "null")
          .append(",\"rejectionReason\":").append(rejectionReason != null ? HttpResponses.jsonString(rejectionReason) : "null");
        if (description != null) {
            sb.append(",\"description\":").append(HttpResponses.jsonString(description));
        }
        String eTitle = eventTitle();
        String eDate = eventDate();
        String eVenue = eventVenue();
        if (eTitle != null) sb.append(",\"eventTitle\":").append(HttpResponses.jsonString(eTitle));
        if (eDate != null) sb.append(",\"eventDate\":").append(HttpResponses.jsonString(eDate));
        if (eVenue != null) sb.append(",\"eventVenue\":").append(HttpResponses.jsonString(eVenue));
        sb.append("}");
        return sb.toString();
    }
}
