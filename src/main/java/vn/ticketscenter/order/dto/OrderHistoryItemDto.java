package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.money;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

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
        return object(
                field("id", text(id)),
                field("orderCode", text(orderCode)),
                field("eventId", text(eventId)),
                field("eventTitle", text(eventTitle)),
                field("venueName", text(venueName)),
                field("startTime", text(startTime)),
                field("subtotalAmount", money(subtotalAmount)),
                field("discountAmount", money(discountAmount)),
                field("totalAmount", money(totalAmount)),
                field("status", text(status)),
                field("createdAt", text(createdAt)),
                field("paidAt", text(paidAt)),
                field("couponCode", nullableText(couponCode)),
                field("ticketCount", number(ticketCount)));
    }
}
