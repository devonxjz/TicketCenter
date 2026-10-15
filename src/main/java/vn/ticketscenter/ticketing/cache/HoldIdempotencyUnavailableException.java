package vn.ticketscenter.ticketing.cache;

import vn.ticketscenter.config.error.AppException;

public final class HoldIdempotencyUnavailableException extends AppException {
    public HoldIdempotencyUnavailableException() {
        super(503, "HOLD_UNAVAILABLE", "Hold idempotency is unavailable");
    }
}
