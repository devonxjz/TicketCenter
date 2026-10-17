package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Tóm tắt đơn hàng hiển thị trong lịch sử mua vé (LIEM-06 / UI-06).
 */
public record OrderHistoryItemDto(
        UUID id,
        String orderCode,
        UUID eventId,
        String eventTitle,
        String venueName,
        Instant startTime,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        Instant paidAt,
        String couponCode,
        long ticketCount) {
    public OrderHistoryItemDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderCode, "orderCode is required");
        Objects.requireNonNull(status, "status is required");
    }

    public String toJson() {
        return "{\"id\":\""
                + id
                + "\","
                + "\"orderCode\":\""
                + escape(orderCode)
                + "\","
                + "\"eventId\":\""
                + (eventId != null ? eventId.toString() : "")
                + "\","
                + "\"eventTitle\":\""
                + escape(eventTitle != null ? eventTitle : "")
                + "\","
                + "\"venueName\":\""
                + escape(venueName != null ? venueName : "")
                + "\","
                + "\"startTime\":\""
                + (startTime != null ? startTime.toString() : "")
                + "\","
                + "\"subtotalAmount\":"
                + (subtotalAmount != null ? subtotalAmount : BigDecimal.ZERO)
                + ","
                + "\"discountAmount\":"
                + (discountAmount != null ? discountAmount : BigDecimal.ZERO)
                + ","
                + "\"totalAmount\":"
                + (totalAmount != null ? totalAmount : BigDecimal.ZERO)
                + ","
                + "\"status\":\""
                + escape(status)
                + "\","
                + "\"createdAt\":\""
                + (createdAt != null ? createdAt.toString() : "")
                + "\","
                + "\"paidAt\":\""
                + (paidAt != null ? paidAt.toString() : "")
                + "\","
                + "\"couponCode\":"
                + (couponCode != null ? "\"" + escape(couponCode) + "\"" : "null")
                + ","
                + "\"ticketCount\":"
                + ticketCount
                + "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
