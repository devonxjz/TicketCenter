package vn.ticketscenter.identity.error;

import vn.ticketscenter.config.error.AppException;

import java.util.Objects;

/** A failure of an identity use case that maps to a specific HTTP response. */
public final class IdentityException extends AppException {

    private final IdentityError error;

    public IdentityException(IdentityError error, String message) {
        super(error.status(), error.code(), message);
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public IdentityException(IdentityError error, String message, Throwable cause) {
        super(error.status(), error.code(), message, cause);
        this.error = Objects.requireNonNull(error, "error must not be null");
    }

    public IdentityError error() {
        return error;
    }
}
