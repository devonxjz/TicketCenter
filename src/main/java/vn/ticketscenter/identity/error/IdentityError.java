package vn.ticketscenter.identity.error;

/**
 * Failures the authentication, OTP and password-reset flows report to clients. {@link #code()} is the
 * {@code error.code} of the JSON envelope and {@link #status()} the HTTP status; both are part of the
 * public API contract, so several constants deliberately share a code.
 */
public enum IdentityError {
    INVALID_JSON(400, "INVALID_JSON"),
    VALIDATION_FAILED(400, "VALIDATION_FAILED"),
    EMAIL_ALREADY_EXISTS(409, "IDENTITY_CONFLICT"),
    USERNAME_ALREADY_EXISTS(409, "IDENTITY_CONFLICT"),
    IDENTITY_CONFLICT(409, "IDENTITY_CONFLICT"),
    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS"),
    ACCOUNT_DISABLED(401, "ACCOUNT_DISABLED"),
    COOLDOWN_ACTIVE(429, "COOLDOWN_ACTIVE"),
    EMAIL_SEND_FAILED(500, "EMAIL_SEND_FAILED"),
    OTP_ERROR(400, "OTP_ERROR"),
    OTP_INVALID(400, "OTP_INVALID"),
    OTP_NOT_FOUND(400, "OTP_NOT_FOUND"),
    OTP_ALREADY_USED(400, "OTP_ALREADY_USED"),
    OTP_EXPIRED(400, "OTP_EXPIRED"),
    OTP_MAX_ATTEMPTS_EXCEEDED(400, "OTP_MAX_ATTEMPTS_EXCEEDED"),
    INVALID_TOKEN(400, "INVALID_TOKEN");

    private final int status;
    private final String code;

    IdentityError(int status, String code) {
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
