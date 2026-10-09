package vn.ticketscenter.fulfillment.checkin.dto;

import vn.ticketscenter.config.web.HttpResponses;
import java.time.Instant;
import java.util.UUID;

public record CheckInEventSummaryDto(UUID eventId, String title, String venue,
        Instant startTime, Instant endTime, Instant checkInOpenTime, boolean isCheckInOpen,
        long totalTicketsIssued, long totalCheckedIn, String status) {
    public String toJson() {
        return "{\"eventId\":\"" + eventId + "\",\"title\":" + HttpResponses.jsonString(title)
                + ",\"venue\":" + HttpResponses.jsonString(venue)
                + ",\"startTime\":\"" + startTime + "\",\"endTime\":\"" + endTime
                + "\",\"checkInOpenTime\":\"" + checkInOpenTime + "\",\"isCheckInOpen\":" + isCheckInOpen
                + ",\"totalTicketsIssued\":" + totalTicketsIssued + ",\"totalCheckedIn\":" + totalCheckedIn
                + ",\"status\":\"" + status + "\"}";
    }
}
