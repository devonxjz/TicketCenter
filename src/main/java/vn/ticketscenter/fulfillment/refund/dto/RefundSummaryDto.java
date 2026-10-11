package vn.ticketscenter.fulfillment.refund.dto;

import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record RefundSummaryDto(UUID refundId,UUID orderId,String totalRefundAmount,String status,int ticketCount,
                               Instant createdAt,String eventName,String reason,String rejectionReason) {
    public String json() {
        return "{\"refundId\":"+text(refundId.toString())+",\"orderId\":"+text(orderId.toString())
                +",\"totalRefundAmount\":"+text(totalRefundAmount)+",\"status\":"+text(status)+",\"ticketCount\":"+ticketCount
                +",\"createdAt\":"+text(createdAt.toString())+",\"eventName\":"+text(eventName)+",\"reason\":"+text(reason)
                +",\"rejectionReason\":"+text(rejectionReason)+"}";
    }
}
