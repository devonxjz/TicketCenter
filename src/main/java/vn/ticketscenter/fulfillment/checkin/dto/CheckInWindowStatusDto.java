package vn.ticketscenter.fulfillment.checkin.dto;

import java.time.Instant;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

public record CheckInWindowStatusDto(
        UUID eventId,
        boolean isOpen,
        String status,
        Instant windowStart,
        Instant windowEnd,
        Instant currentServerTime,
        String message) {
    public String toJson() {
        return object(
                field("eventId", text(String.valueOf(eventId))),
                field("isOpen", number(isOpen)),
                field("status", text(String.valueOf(status))),
                field("windowStart", text(String.valueOf(windowStart))),
                field("windowEnd", text(String.valueOf(windowEnd))),
                field("currentServerTime", text(String.valueOf(currentServerTime))),
                field("message", nullableText(message)));
    }
}
