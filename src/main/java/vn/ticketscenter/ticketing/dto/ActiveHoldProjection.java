package vn.ticketscenter.ticketing.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Internal snapshot supplied only after a trusted DB adapter confirms an ACTIVE hold. Not an HTTP DTO. */
public record ActiveHoldProjection(
        UUID id, UUID buyerId, UUID eventId, Instant expiresAt, List<Item> items) {
    public ActiveHoldProjection {
        requireId(id);
        requireId(buyerId);
        requireId(eventId);
        Objects.requireNonNull(expiresAt);
        expiresAt.toEpochMilli();
        items = List.copyOf(items);
        if (items.isEmpty() || items.size() > 8)
            throw new IllegalArgumentException("hold needs 1..8 items");
        int total = 0;
        var seats = new HashSet<UUID>();
        for (Item item : items) {
            total += item.quantity();
            if (item.seatId() != null && !seats.add(item.seatId()))
                throw new IllegalArgumentException("duplicate seat");
        }
        if (total > 8) throw new IllegalArgumentException("hold needs 1..8 tickets");
    }

    public record Item(UUID zoneId, UUID seatId, int quantity, BigDecimal unitPrice) {
        public Item {
            requireId(zoneId);
            if (seatId != null) requireId(seatId);
            if (quantity < 1 || quantity > 8 || (seatId != null && quantity != 1)) {
                throw new IllegalArgumentException("invalid hold item quantity");
            }
            unitPrice = Objects.requireNonNull(unitPrice).setScale(0, RoundingMode.UNNECESSARY);
            if (unitPrice.signum() < 0 || unitPrice.precision() > 19)
                throw new IllegalArgumentException(
                        "unitPrice must be nonnegative DECIMAL(19,0) VND");
        }
    }

    private static void requireId(UUID id) {
        if (id == null || id.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("nonzero UUID required");
    }
}
