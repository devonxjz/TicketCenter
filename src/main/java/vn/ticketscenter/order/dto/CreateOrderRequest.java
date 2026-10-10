package vn.ticketscenter.order.dto;

import java.util.UUID;

public record CreateOrderRequest(UUID holdId) {
    public CreateOrderRequest {
        OrderRequests.requireId(holdId);
    }
}
