package vn.ticketscenter.fulfillment.refund.dto;

import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record RefundSummaryDto(
        UUID refundId,
        UUID orderId,
        String totalRefundAmount,
        String status,
        int ticketCount,
        Instant createdAt,
        String eventName,
        String reason,
        String rejectionReason) {
    public String statusLabel() {
        return switch (status) {
            case "REQUESTED" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt, chờ chuyển tiền";
            case "PROCESSING" -> "Đang chuyển tiền";
            case "COMPLETED" -> "Đã hoàn tiền";
            case "REJECTED" -> "Bị từ chối";
            case "RETRYABLE" -> "Chuyển tiền thất bại, cần xử lý";
            case "NEEDS_RECONCILIATION" -> "Đang xác minh giao dịch";
            default -> status;
        };
    }

    public String json() {
        return "{" + fields() + "}";
    }

    /** The created summary plus a message for the buyer. */
    public String json(String message) {
        return "{" + fields() + ",\"message\":" + text(message) + "}";
    }

    private String fields() {
        return "\"refundId\":"
                + text(refundId.toString())
                + ",\"orderId\":"
                + text(orderId.toString())
                + ",\"totalRefundAmount\":"
                + text(totalRefundAmount)
                + ",\"status\":"
                + text(status)
                + ",\"ticketCount\":"
                + ticketCount
                + ",\"createdAt\":"
                + text(createdAt.toString())
                + ",\"eventName\":"
                + text(eventName)
                + ",\"reason\":"
                + text(reason)
                + ",\"rejectionReason\":"
                + text(rejectionReason);
    }
}
