package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
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
        UUID commissionRuleId) {
    public OrganizationRequestDetailDto(
            UUID organizationId,
            String name,
            String contactEmail,
            String contactPhone,
            String description,
            OrganizationStatus status,
            UUID requesterId,
            Instant submittedAt,
            String rejectionReason) {
        this(
                organizationId,
                name,
                contactEmail,
                contactPhone,
                description,
                status,
                requesterId,
                submittedAt,
                rejectionReason,
                null);
    }

    public static OrganizationRequestDetailDto from(Organization organization) {
        if (organization == null) {
            return null;
        }
        UUID reqId =
                organization.getRequester() != null ? organization.getRequester().getId() : null;
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
                null);
    }

    public String eventTitle() {
        return parseTag(description, "Event");
    }

    public String eventDate() {
        return parseTag(description, "Date");
    }

    public String eventStartTime() {
        return parseTag(description, "Start");
    }

    public String eventEndTime() {
        return parseTag(description, "End");
    }

    public String eventVenue() {
        return parseTag(description, "Venue");
    }

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
            String content =
                    end != -1
                            ? description.substring(start + 8, end)
                            : description.substring(start + 8);
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
                if (key.equalsIgnoreCase("Event") && p.startsWith("Sự kiện "))
                    return p.substring(8).trim();
                if (key.equalsIgnoreCase("Date") && p.startsWith("Ngày "))
                    return p.substring(5).trim();
                if (key.equalsIgnoreCase("Venue") && p.startsWith("Địa điểm "))
                    return p.substring(9).trim();
            }
        }
        return null;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"organizationId\":")
                .append(json(organizationId))
                .append(",\"name\":")
                .append(json(name))
                .append(",\"contactEmail\":")
                .append(json(contactEmail))
                .append(",\"contactPhone\":")
                .append(json(contactPhone))
                .append(",\"description\":")
                .append(json(description))
                .append(",\"status\":")
                .append(json(status))
                .append(",\"requesterId\":")
                .append(json(requesterId))
                .append(",\"submittedAt\":")
                .append(json(submittedAt))
                .append(",\"rejectionReason\":")
                .append(json(rejectionReason));
        if (commissionRuleId != null) {
            sb.append(",\"commissionRuleId\":").append(json(commissionRuleId));
        }
        appendIfPresent(sb, "eventTitle", eventTitle());
        appendIfPresent(sb, "eventDate", eventDate());
        appendIfPresent(sb, "eventStartTime", eventStartTime());
        appendIfPresent(sb, "eventEndTime", eventEndTime());
        appendIfPresent(sb, "eventVenue", eventVenue());
        appendIfPresent(sb, "cleanDescription", cleanDescription());
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
