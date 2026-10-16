package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import vn.ticketscenter.order.model.OrderStatus;

/** Stored prices and totals only. Adapters to Long models must use longValueExact(). */
public record OrderSnapshot(
        UUID orderId,
        String orderCode,
        UUID buyerId,
        UUID eventId,
        UUID holdId,
        OrderStatus status,
        Instant expiresAt,
        List<Item> items,
        Totals totals,
        Optional<String> appliedCouponCode) {
    public OrderSnapshot {
        OrderRequests.requireId(orderId);
        OrderRequests.requireId(buyerId);
        OrderRequests.requireId(eventId);
        OrderRequests.requireId(holdId);
        OrderRequests.requireText(orderCode, 64);
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(totals, "totals");
        Objects.requireNonNull(appliedCouponCode, "appliedCouponCode");
        appliedCouponCode.ifPresent(code -> OrderRequests.requireText(code, 80));
        items = List.copyOf(items);
        if (items.isEmpty() || items.size() > 8)
            throw new IllegalArgumentException("Expected 1 to 8 items");
        var ids = new HashSet<UUID>();
        int quantity = 0;
        BigDecimal subtotal = BigDecimal.ZERO;
        for (Item item : items) {
            if (!ids.add(item.orderItemId()))
                throw new IllegalArgumentException("Duplicate item identity");
            quantity += item.quantity();
            subtotal = subtotal.add(item.lineTotal());
        }
        if (quantity > 8) throw new IllegalArgumentException("Expected 1 to 8 tickets");
        if (!subtotal.equals(totals.subtotal()))
            throw new IllegalArgumentException("Item subtotal differs from Order");
    }

    public record Item(
            UUID orderItemId,
            UUID zoneId,
            UUID seatId,
            String zoneNameSnapshot,
            Optional<String> seatLabelSnapshot,
            int quantity,
            BigDecimal unitPrice) {
        public Item {
            OrderRequests.requireId(orderItemId);
            OrderRequests.requireId(zoneId);
            if (seatId != null) OrderRequests.requireId(seatId);
            OrderRequests.requireText(zoneNameSnapshot, 120);
            Objects.requireNonNull(seatLabelSnapshot, "seatLabelSnapshot");
            seatLabelSnapshot.ifPresent(label -> OrderRequests.requireText(label, 50));
            if (quantity < 1
                    || quantity > 8
                    || (seatId != null && quantity != 1)
                    || (seatId == null && seatLabelSnapshot.isPresent())) {
                throw new IllegalArgumentException("Invalid item quantity");
            }
            unitPrice = money(unitPrice);
            money(unitPrice.multiply(BigDecimal.valueOf(quantity)));
        }

        public BigDecimal lineTotal() {
            return unitPrice.multiply(BigDecimal.valueOf(quantity));
        }
    }

    public record Totals(BigDecimal subtotal, BigDecimal discountAmount, BigDecimal totalAmount) {
        public Totals {
            subtotal = money(subtotal);
            discountAmount = money(discountAmount);
            totalAmount = money(totalAmount);
            BigDecimal cap =
                    subtotal.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.DOWN);
            if (discountAmount.compareTo(cap) > 0
                    || !subtotal.subtract(discountAmount).equals(totalAmount)) {
                throw new IllegalArgumentException("Inconsistent Order totals");
            }
        }
    }

    private static BigDecimal money(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        BigDecimal whole;
        try {
            whole = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Fractional VND", e);
        }
        if (whole.signum() < 0 || whole.precision() > 19) {
            throw new IllegalArgumentException("Amount outside nonnegative decimal(19,0)");
        }
        return whole;
    }
}
