package vn.ticketscenter.fulfillment.ticket.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

/**
 * A ticket as the "My tickets" list shows it (LIEM-05).
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
        return object(
                field("id", text(id)),
                field("orderId", text(orderId)),
                field("ticketCode", text(ticketCode)),
                field("eventTitle", text(eventTitle)),
                field("venueName", text(venueName)),
                field("startTime", text(startTime)),
                field("zoneName", text(zoneName)),
                field("seatLabel", text(seatLabel)),
                field("paidAmount", number(paidAmount)),
                field("status", text(status)),
                field("issuedAt", text(issuedAt)),
                field("version", number(version)));
    }
}
