package vn.ticketscenter.order.dto;

import vn.ticketscenter.config.web.DtoSerializer;
import vn.ticketscenter.config.web.JsonSerializable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

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
        List<OrderTicketDetail> tickets) implements JsonSerializable {
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
            BigDecimal lineTotal) {}

    public record OrderTicketDetail(UUID id, String ticketCode, String status, long paidAmount) {}

    @Override
    public String toJson() {
        return DtoSerializer.toJson(this);
    }
}
