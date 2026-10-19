package vn.ticketscenter.fulfillment.ticket.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

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
        return object(
                field("id", text(id)),
                field("orderId", text(orderId)),
                field("orderCode", text(orderCode)),
                field("ticketCode", text(ticketCode)),
                field("eventId", text(eventId)),
                field("eventTitle", text(eventTitle)),
                field("venueName", text(venueName)),
                field("venueAddress", text(venueAddress)),
                field("startTime", text(startTime)),
                field("endTime", text(endTime)),
                field("zoneName", text(zoneName)),
                field("seatLabel", text(seatLabel)),
                field("paidAmount", number(paidAmount)),
                field("status", text(status)),
                field("issuedAt", text(issuedAt)),
                field("version", number(version)));
    }
}
