package vn.ticketscenter.config.error;

/** An external service (payOS, SMTP, Redis, image storage...) failed or did not answer: shown as 502. */
public class UpstreamException extends AppException {

    public UpstreamException(String code, String message) {
        super(502, code, message);
    }

    public UpstreamException(String code, String message, Throwable cause) {
        super(502, code, message, cause);
    }
}
