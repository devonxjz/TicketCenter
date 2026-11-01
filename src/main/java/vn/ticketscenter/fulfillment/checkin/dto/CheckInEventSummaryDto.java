package vn.ticketscenter.fulfillment.checkin.dto;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

public record CheckInEventSummaryDto(
        UUID eventId,
        String title,
        String venue,
        Instant startTime,
        Instant endTime,
        Instant checkInOpenTime,
        boolean isCheckInOpen,
        long totalTicketsIssued,
        long totalCheckedIn,
        String status) {
    public String toJson() {
        return object(
                field("eventId", text(String.valueOf(eventId))),
                field("title", nullableText(title)),
                field("venue", nullableText(venue)),
                field("startTime", text(String.valueOf(startTime))),
                field("endTime", text(String.valueOf(endTime))),
                field("checkInOpenTime", text(String.valueOf(checkInOpenTime))),
                field("isCheckInOpen", number(isCheckInOpen)),
                field("totalTicketsIssued", number(totalTicketsIssued)),
                field("totalCheckedIn", number(totalCheckedIn)),
                field("status", text(String.valueOf(status))));
    }
}
