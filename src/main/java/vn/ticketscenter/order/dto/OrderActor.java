package vn.ticketscenter.order.dto;

import java.util.UUID;

public record OrderActor(UUID id, int authVersion) {
    public OrderActor {
        OrderRequests.requireId(id);
        if (authVersion < 0) throw new IllegalArgumentException("Invalid auth version");
    }
}
