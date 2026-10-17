package vn.ticketscenter.fulfillment.refund.dto;

import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record AdminRefundSummaryDto(
        UUID refundId,
        UUID orderId,
        UUID buyerId,
        String buyerName,
        String buyerEmail,
        String eventName,
        String totalAmount,
        int ticketCount,
        String reason,
        String status,
        Instant createdAt,
        String rejectionReason,
        boolean canDecide,
        boolean canRetry) {
    public String json() {
        return "{\"refundId\":"
                + text(refundId.toString())
                + ",\"orderId\":"
                + text(orderId.toString())
                + ",\"buyer\":{\"userId\":"
                + text(buyerId.toString())
                + ",\"fullName\":"
                + text(buyerName)
                + ",\"email\":"
                + text(buyerEmail)
                + "}"
                + ",\"eventName\":"
                + text(eventName)
                + ",\"totalAmount\":"
                + text(totalAmount)
                + ",\"ticketCount\":"
                + ticketCount
                + ",\"reason\":"
                + text(reason)
                + ",\"status\":"
                + text(status)
                + ",\"createdAt\":"
                + text(createdAt.toString())
                + ",\"rejectionReason\":"
                + text(rejectionReason)
                + ",\"canDecide\":"
                + canDecide
                + ",\"canRetry\":"
                + canRetry
                + "}";
    }
}
