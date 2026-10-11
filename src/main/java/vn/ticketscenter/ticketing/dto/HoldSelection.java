package vn.ticketscenter.ticketing.dto;

import java.util.UUID;

public record HoldSelection(UUID zoneId, UUID seatId, int quantity) {
    public HoldSelection {
        if (zoneId == null || zoneId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("zoneId must be a nonzero UUID");
        }
        if (seatId != null && seatId.equals(new UUID(0, 0))) {
            throw new IllegalArgumentException("seatId must be a nonzero UUID when present");
        }
        if (quantity < 1 || quantity > 8 || (seatId != null && quantity != 1)) {
            throw new IllegalArgumentException("quantity must be 1..8, or 1 for a seat");
        }
    }
}
