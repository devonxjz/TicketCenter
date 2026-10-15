package vn.ticketscenter.identity.error;

import java.util.Objects;

/** A failure of an identity use case that maps to a specific HTTP response. */
public final class IdentityException extends RuntimeException {

    private final IdentityError error;

    public IdentityException(IdentityError error, String message) {
        super(message);
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public IdentityException(IdentityError error, String message, Throwable cause) {
        super(message, cause);
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public IdentityError error() {
        return error;
    }
}
