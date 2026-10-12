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
        String rejectionReason,
        UUID commissionRuleId
) {
    public OrganizationRequestDetailDto(
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
        this(organizationId, name, contactEmail, contactPhone, description, status, requesterId, submittedAt, rejectionReason, null);
    }

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
                organization.getRejectionReason(),
                null
        );
    }

    public String eventTitle() { return parseTag(description, "Event"); }
    public String eventDate() { return parseTag(description, "Date"); }
    public String eventStartTime() { return parseTag(description, "Start"); }
    public String eventEndTime() { return parseTag(description, "End"); }
    public String eventVenue() { return parseTag(description, "Venue"); }
    public String cleanDescription() {
        if (description == null) return null;
        int idx = description.indexOf("[Event:");
        if (idx == -1) idx = description.indexOf("[Lịch trình dự kiến]");
        if (idx != -1) {
            String clean = description.substring(0, idx).trim();
            return clean.isEmpty() ? null : clean;
        }
        return description;
    }

    static String parseTag(String description, String key) {
        if (description == null) return null;
        int start = description.indexOf("[Event: ");
        if (start != -1) {
            int end = description.indexOf("]", start);
            String content = end != -1 ? description.substring(start + 8, end) : description.substring(start + 8);
            String[] parts = content.split("\\|");
            for (String part : parts) {
                String p = part.trim();
                if (key.equalsIgnoreCase("Event")) {
                    if (!p.contains(":")) return p.isEmpty() ? null : p;
                } else if (p.toLowerCase().startsWith(key.toLowerCase() + ":")) {
                    return p.substring(key.length() + 1).trim();
                }
            }
        }
        if (description.contains("[Lịch trình dự kiến]")) {
            int s = description.indexOf("[Lịch trình dự kiến]") + 20;
            String content = description.substring(s).trim();
            String[] parts = content.split("\\|");
            for (String part : parts) {
                String p = part.trim();
                if (key.equalsIgnoreCase("Event") && p.startsWith("Sự kiện ")) return p.substring(8).trim();
                if (key.equalsIgnoreCase("Date") && p.startsWith("Ngày ")) return p.substring(5).trim();
                if (key.equalsIgnoreCase("Venue") && p.startsWith("Địa điểm ")) return p.substring(9).trim();
            }
        }
        return null;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"organizationId\":").append(organizationId != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(organizationId.toString()) : "null")
          .append(",\"name\":").append(name != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(name) : "null")
          .append(",\"contactEmail\":").append(contactEmail != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(contactEmail) : "null")
          .append(",\"contactPhone\":").append(contactPhone != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(contactPhone) : "null")
          .append(",\"description\":").append(description != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(description) : "null")
          .append(",\"status\":").append(status != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(status.name()) : "null")
          .append(",\"requesterId\":").append(requesterId != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(requesterId.toString()) : "null")
          .append(",\"submittedAt\":").append(submittedAt != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(submittedAt.toString()) : "null")
          .append(",\"rejectionReason\":").append(rejectionReason != null ? vn.ticketscenter.config.web.HttpResponses.jsonString(rejectionReason) : "null");
        if (commissionRuleId != null) {
            sb.append(",\"commissionRuleId\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(commissionRuleId.toString()));
        }
        String eTitle = eventTitle();
        String eDate = eventDate();
        String eStart = eventStartTime();
        String eEnd = eventEndTime();
        String eVenue = eventVenue();
        String cleanDesc = cleanDescription();
        if (eTitle != null) sb.append(",\"eventTitle\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(eTitle));
        if (eDate != null) sb.append(",\"eventDate\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(eDate));
        if (eStart != null) sb.append(",\"eventStartTime\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(eStart));
        if (eEnd != null) sb.append(",\"eventEndTime\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(eEnd));
        if (eVenue != null) sb.append(",\"eventVenue\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(eVenue));
        if (cleanDesc != null) sb.append(",\"cleanDescription\":").append(vn.ticketscenter.config.web.HttpResponses.jsonString(cleanDesc));
        sb.append("}");
        return sb.toString();
    }
}
