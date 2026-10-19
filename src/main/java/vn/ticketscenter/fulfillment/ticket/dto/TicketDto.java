package vn.ticketscenter.fulfillment.ticket.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unified ticket DTO (summary + detail).
 * Use nullable fields for detail-only fields (orderCode, eventId, venueAddress, endTime).
 */
public record TicketDto(
        UUID id,
        UUID orderId,
        String orderCode,
        String ticketCode,
        UUID eventId,
        String eventTitle,
        String venueName,
        String venueAddress,
        Instant startTime,
        Instant endTime,
        String zoneName,
        String seatLabel,
        long paidAmount,
        String status,
        Instant issuedAt,
        long version) {

    public TicketDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(ticketCode, "ticketCode is required");
        Objects.requireNonNull(eventTitle, "eventTitle is required");
        Objects.requireNonNull(status, "status is required");
    }

    public String toJson() {
        StringBuilder json = new StringBuilder("{\"id\":\"").append(id)
            .append("\",\"orderId\":\"").append(orderId)
            .append("\",\"ticketCode\":\"").append(escape(ticketCode))
            .append("\",\"eventTitle\":\"").append(escape(eventTitle))
            .append("\",\"venueName\":\"").append(escape(venueName != null ? venueName : ""))
            .append("\",\"startTime\":\"").append(startTime != null ? startTime : "")
            .append("\",\"zoneName\":\"").append(escape(zoneName != null ? zoneName : ""))
            .append("\",\"seatLabel\":\"").append(escape(seatLabel != null ? seatLabel : ""))
            .append("\",\"paidAmount\":").append(paidAmount)
            .append(",\"status\":\"").append(escape(status))
            .append("\",\"issuedAt\":\"").append(issuedAt != null ? issuedAt : "")
            .append("\",\"version\":").append(version);

        if (orderCode != null) {
            json.append(",\"orderCode\":\"").append(escape(orderCode)).append("\"");
        }
        if (eventId != null) {
            json.append(",\"eventId\":\"").append(eventId).append("\"");
        }
        if (venueAddress != null) {
            json.append(",\"venueAddress\":\"").append(escape(venueAddress)).append("\"");
        }
        if (endTime != null) {
            json.append(",\"endTime\":\"").append(endTime).append("\"");
        }

        return json.append("}").toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
