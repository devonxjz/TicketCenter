package vn.ticketscenter.fulfillment.ticket.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Chi tiết một vé và thông tin sự kiện liên quan (LIEM-05).
 */
public record TicketDetailDto(
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
    public TicketDetailDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderId, "orderId is required");
        Objects.requireNonNull(ticketCode, "ticketCode is required");
        Objects.requireNonNull(eventTitle, "eventTitle is required");
        Objects.requireNonNull(status, "status is required");
    }

    public String toJson() {
        return "{\"id\":\""
                + id
                + "\","
                + "\"orderId\":\""
                + orderId
                + "\","
                + "\"orderCode\":\""
                + escape(orderCode != null ? orderCode : "")
                + "\","
                + "\"ticketCode\":\""
                + escape(ticketCode)
                + "\","
                + "\"eventId\":\""
                + (eventId != null ? eventId.toString() : "")
                + "\","
                + "\"eventTitle\":\""
                + escape(eventTitle)
                + "\","
                + "\"venueName\":\""
                + escape(venueName != null ? venueName : "")
                + "\","
                + "\"venueAddress\":\""
                + escape(venueAddress != null ? venueAddress : "")
                + "\","
                + "\"startTime\":\""
                + (startTime != null ? startTime.toString() : "")
                + "\","
                + "\"endTime\":\""
                + (endTime != null ? endTime.toString() : "")
                + "\","
                + "\"zoneName\":\""
                + escape(zoneName != null ? zoneName : "")
                + "\","
                + "\"seatLabel\":\""
                + escape(seatLabel != null ? seatLabel : "")
                + "\","
                + "\"paidAmount\":"
                + paidAmount
                + ","
                + "\"status\":\""
                + escape(status)
                + "\","
                + "\"issuedAt\":\""
                + (issuedAt != null ? issuedAt.toString() : "")
                + "\","
                + "\"version\":"
                + version
                + "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
