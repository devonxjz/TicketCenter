package vn.ticketscenter.config.error;

/**
 * Root of every business or infrastructure error. It carries the HTTP status and a stable error code so
 * {@code ErrorPageServlet} (and the API servlets) build one reply shape without guessing from the exception type.
 */
public class AppException extends RuntimeException {

    private final int status;
    private final String code;

    public AppException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public AppException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("invalid HTTP status: " + status);
        }
        this.status = status;
        this.code = code == null || code.isBlank() ? "REQUEST_FAILED" : code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }
}
