package vn.ticketscenter.event.cancellation.dto;

import java.time.Instant;
import java.util.UUID;
import static vn.ticketscenter.config.web.HttpResponses.jsonString;

public record EventCancelResponseDto(UUID eventId, Instant cancelledAt, long totalOrdersToRefund) {
    public String json() {
        return "{\"success\":true,\"eventId\":" + jsonString(eventId.toString())
                + ",\"status\":\"CANCELLED\",\"cancelledAt\":" + jsonString(cancelledAt.toString())
                + ",\"totalOrdersToRefund\":" + totalOrdersToRefund
                + ",\"message\":" + jsonString("Sự kiện đã hủy. Hệ thống đang tạo yêu cầu hoàn tiền tự động.") + "}";
    }
}
