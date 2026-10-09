package vn.ticketscenter.ticketing.cache;

public final class HoldIdempotencyUnavailableException extends RuntimeException {
    public HoldIdempotencyUnavailableException() {
        super("Hold idempotency is unavailable");
    }
}
