package vn.ticketscenter.fulfillment.refund.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Distinct attempt ID is the provider idempotency key. */
public record RefundTransferRequest(UUID refundId, UUID attemptId, BigDecimal amount) {
    public RefundTransferRequest {
        Objects.requireNonNull(refundId);
        Objects.requireNonNull(attemptId);
        Objects.requireNonNull(amount);
        if (amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 0
                || amount.precision() - amount.scale() > 19) {
            throw new IllegalArgumentException("Positive integer VND required");
        }
    }
}
