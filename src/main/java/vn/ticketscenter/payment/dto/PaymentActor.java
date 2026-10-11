package vn.ticketscenter.payment.dto;

import java.util.Objects;
import java.util.UUID;

public record PaymentActor(UUID id, int authVersion) {
    public PaymentActor {
        Objects.requireNonNull(id, "actor id is required");
        if (authVersion < 0) {
            throw new IllegalArgumentException("auth version cannot be negative");
        }
    }
}
