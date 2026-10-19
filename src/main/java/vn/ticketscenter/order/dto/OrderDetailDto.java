package vn.ticketscenter.order.dto;

import vn.ticketscenter.config.web.HttpResponses;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Chi tiết đầy đủ của đơn hàng (LIEM-06 / UI-06).
 */
public record OrderDetailDto(
        UUID id,
        UUID buyerId,
        String orderCode,
        UUID eventId,
        String eventTitle,
        String venueName,
        String venueAddress,
        Instant startTime,
        Instant endTime,
        BigDecimal subtotalAmount,
        BigDecimal discountAmount,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        Instant paidAt,
        String couponCode,
        String paymentStatus,
        String txnRef,
        String providerReference,
        Instant paymentCapturedAt,
        List<OrderItemDetail> items,
        List<OrderTicketDetail> tickets) {
    public OrderDetailDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(orderCode, "orderCode is required");
        Objects.requireNonNull(status, "status is required");
        if (items == null) items = List.of();
        if (tickets == null) tickets = List.of();
    }

    public record OrderItemDetail(
            UUID id,
            String zoneName,
            String seatLabel,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {
        public String toJson() {
            return object(
                    field("id", text(id)),
                    field("zoneName", text(zoneName)),
                    field("seatLabel", HttpResponses.jsonString(seatLabel)),
                    field("quantity", String.valueOf(quantity)),
                    field("unitPrice", money(unitPrice)),
                    field("lineTotal", money(lineTotal)));
        }
    }

    public record OrderTicketDetail(UUID id, String ticketCode, String status, long paidAmount) {
        public String toJson() {
            return object(
                    field("id", text(id)),
                    field("ticketCode", text(ticketCode)),
                    field("status", text(status)),
                    field("paidAmount", String.valueOf(paidAmount)));
        }
    }

    public String toJson() {
        return object(
                field("id", text(id)),
                field("buyerId", text(buyerId)),
                field("orderCode", text(orderCode)),
                field("eventId", text(eventId)),
                field("eventTitle", text(eventTitle)),
                field("venueName", text(venueName)),
                field("venueAddress", text(venueAddress)),
                field("startTime", text(startTime)),
                field("endTime", text(endTime)),
                field("subtotalAmount", money(subtotalAmount)),
                field("discountAmount", money(discountAmount)),
                field("totalAmount", money(totalAmount)),
                field("status", text(status)),
                field("createdAt", text(createdAt)),
                field("paidAt", text(paidAt)),
                field("couponCode", HttpResponses.jsonString(couponCode)),
                field("paymentStatus", HttpResponses.jsonString(paymentStatus)),
                field("txnRef", HttpResponses.jsonString(txnRef)),
                field("providerReference", HttpResponses.jsonString(providerReference)),
                field("paymentCapturedAt", text(paymentCapturedAt)),
                field("items", array(items, OrderItemDetail::toJson)),
                field("tickets", array(tickets, OrderTicketDetail::toJson)));
    }

    private static String object(String... fields) {
        return "{" + String.join(",", fields) + "}";
    }

    private static String field(String name, String json) {
        return "\"" + name + "\":" + json;
    }

    private static String text(Object value) {
        return HttpResponses.jsonString(value == null ? "" : value.toString());
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).toString();
    }

    private static <T> String array(List<T> values, Function<T, String> encode) {
        return values.stream().map(encode).collect(Collectors.joining(",", "[", "]"));
    }
}
