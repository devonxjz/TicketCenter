package vn.ticketscenter.fulfillment.ticket.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tóm tắt thông tin vé hiển thị trong danh sách "Vé của tôi" (LIEM-05).
 */
public record TicketSummaryDto(
        UUID id,
        UUID orderId,
        String ticketCode,
        String eventTitle,
        String venueName,
        Instant startTime,
        String zoneName,
        String seatLabel,
        long paidAmount,
        String status,
        Instant issuedAt,
        long version) {
    public TicketSummaryDto {
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
                + "\"ticketCode\":\""
                + escape(ticketCode)
                + "\","
                + "\"eventTitle\":\""
                + escape(eventTitle)
                + "\","
                + "\"venueName\":\""
                + escape(venueName != null ? venueName : "")
                + "\","
                + "\"startTime\":\""
                + (startTime != null ? startTime.toString() : "")
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
