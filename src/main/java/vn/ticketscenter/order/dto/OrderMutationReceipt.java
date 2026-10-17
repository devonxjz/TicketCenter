package vn.ticketscenter.order.dto;

import java.time.Instant;
import java.util.UUID;

public record OrderMutationReceipt(
        int httpStatus,
        String body,
        boolean replayed,
        UUID orderId,
        UUID holdId,
        Instant expiresAt) {}
