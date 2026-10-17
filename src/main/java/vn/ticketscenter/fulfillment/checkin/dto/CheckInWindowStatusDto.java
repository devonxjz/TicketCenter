package vn.ticketscenter.fulfillment.checkin.dto;

import vn.ticketscenter.config.web.HttpResponses;
import java.time.Instant;
import java.util.UUID;

public record CheckInWindowStatusDto(
        UUID eventId,
        boolean isOpen,
        String status,
        Instant windowStart,
        Instant windowEnd,
        Instant currentServerTime,
        String message) {
    public String toJson() {
        return "{\"eventId\":\""
                + eventId
                + "\",\"isOpen\":"
                + isOpen
                + ",\"status\":\""
                + status
                + "\",\"windowStart\":\""
                + windowStart
                + "\",\"windowEnd\":\""
                + windowEnd
                + "\",\"currentServerTime\":\""
                + currentServerTime
                + "\",\"message\":"
                + HttpResponses.jsonString(message)
                + "}";
    }
}
