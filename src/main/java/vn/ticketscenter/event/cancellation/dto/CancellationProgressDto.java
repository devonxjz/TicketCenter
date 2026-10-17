package vn.ticketscenter.event.cancellation.dto;

import static vn.ticketscenter.config.web.HttpResponses.jsonString;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** COMPLETED describes obligation preparation; transfer results remain separate. */
public record CancellationProgressDto(UUID eventId, UUID organizationId, String status, String reason,
        Instant cancelledAt, long totalOrders, long processedOrders, UUID lastProcessedOrderId,
        Instant updatedAt, long successfulRefunds, long failedRefunds, long unknownRefunds,
        long pendingRefunds, long usedTicketExceptions) {
    public String json() {
        String percentage = totalOrders == 0 ? "100.0" : BigDecimal.valueOf(processedOrders)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalOrders), 1, RoundingMode.DOWN)
                .toPlainString();
        return "{\"eventId\":" + jsonString(eventId.toString()) + ",\"status\":" + jsonString(status)
                + ",\"reason\":" + jsonString(reason) + ",\"cancelledAt\":" + jsonString(cancelledAt.toString())
                + ",\"totalOrders\":" + totalOrders + ",\"processedOrders\":" + processedOrders
                + ",\"progressPercentage\":" + percentage + ",\"updatedAt\":" + jsonString(updatedAt.toString())
                + ",\"successfulRefunds\":" + successfulRefunds + ",\"failedRefunds\":" + failedRefunds
                + ",\"unknownRefunds\":" + unknownRefunds + ",\"pendingRefunds\":" + pendingRefunds
                + ",\"usedTicketExceptions\":" + usedTicketExceptions + "}";
    }
}
