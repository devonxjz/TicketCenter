package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.Organization;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.optional;

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
        return object(
                field("organizationId", nullableText(organizationId)),
                field("name", nullableText(name)),
                field("contactEmail", nullableText(contactEmail)),
                field("contactPhone", nullableText(contactPhone)),
                field("description", nullableText(description)),
                field("status", nullableText(status)),
                field("requesterId", nullableText(requesterId)),
                field("submittedAt", nullableText(submittedAt)),
                field("rejectionReason", nullableText(rejectionReason)),
                optional("commissionRuleId", commissionRuleId),
                optional("eventTitle", eventTitle()),
                optional("eventDate", eventDate()),
                optional("eventStartTime", eventStartTime()),
                optional("eventEndTime", eventEndTime()),
                optional("eventVenue", eventVenue()),
                optional("cleanDescription", cleanDescription()));
    }
}
