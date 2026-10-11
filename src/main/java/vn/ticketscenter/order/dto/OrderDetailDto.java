package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
        List<OrderTicketDetail> tickets
) {
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
            BigDecimal lineTotal
    ) {
        public String toJson() {
            return "{\"id\":\"" + id + "\","
                    + "\"zoneName\":\"" + escape(zoneName) + "\","
                    + "\"seatLabel\":" + (seatLabel != null ? "\"" + escape(seatLabel) + "\"" : "null") + ","
                    + "\"quantity\":" + quantity + ","
                    + "\"unitPrice\":" + (unitPrice != null ? unitPrice : BigDecimal.ZERO) + ","
                    + "\"lineTotal\":" + (lineTotal != null ? lineTotal : BigDecimal.ZERO) + "}";
        }
    }

    public record OrderTicketDetail(
            UUID id,
            String ticketCode,
            String status,
            long paidAmount
    ) {
        public String toJson() {
            return "{\"id\":\"" + id + "\","
                    + "\"ticketCode\":\"" + escape(ticketCode) + "\","
                    + "\"status\":\"" + escape(status) + "\","
                    + "\"paidAmount\":" + paidAmount + "}";
        }
    }

    public String toJson() {
        String itemsJson = items.stream().map(OrderItemDetail::toJson).collect(Collectors.joining(","));
        String ticketsJson = tickets.stream().map(OrderTicketDetail::toJson).collect(Collectors.joining(","));

        return "{\"id\":\"" + id + "\","
                + "\"buyerId\":\"" + (buyerId != null ? buyerId.toString() : "") + "\","
                + "\"orderCode\":\"" + escape(orderCode) + "\","
                + "\"eventId\":\"" + (eventId != null ? eventId.toString() : "") + "\","
                + "\"eventTitle\":\"" + escape(eventTitle != null ? eventTitle : "") + "\","
                + "\"venueName\":\"" + escape(venueName != null ? venueName : "") + "\","
                + "\"venueAddress\":\"" + escape(venueAddress != null ? venueAddress : "") + "\","
                + "\"startTime\":\"" + (startTime != null ? startTime.toString() : "") + "\","
                + "\"endTime\":\"" + (endTime != null ? endTime.toString() : "") + "\","
                + "\"subtotalAmount\":" + (subtotalAmount != null ? subtotalAmount : BigDecimal.ZERO) + ","
                + "\"discountAmount\":" + (discountAmount != null ? discountAmount : BigDecimal.ZERO) + ","
                + "\"totalAmount\":" + (totalAmount != null ? totalAmount : BigDecimal.ZERO) + ","
                + "\"status\":\"" + escape(status) + "\","
                + "\"createdAt\":\"" + (createdAt != null ? createdAt.toString() : "") + "\","
                + "\"paidAt\":\"" + (paidAt != null ? paidAt.toString() : "") + "\","
                + "\"couponCode\":" + (couponCode != null ? "\"" + escape(couponCode) + "\"" : "null") + ","
                + "\"paymentStatus\":" + (paymentStatus != null ? "\"" + escape(paymentStatus) + "\"" : "null") + ","
                + "\"txnRef\":" + (txnRef != null ? "\"" + escape(txnRef) + "\"" : "null") + ","
                + "\"providerReference\":" + (providerReference != null ? "\"" + escape(providerReference) + "\"" : "null") + ","
                + "\"paymentCapturedAt\":\"" + (paymentCapturedAt != null ? paymentCapturedAt.toString() : "") + "\","
                + "\"items\":[" + itemsJson + "],"
                + "\"tickets\":[" + ticketsJson + "]}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
