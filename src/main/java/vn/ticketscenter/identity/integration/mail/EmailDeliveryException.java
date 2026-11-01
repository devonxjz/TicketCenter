package vn.ticketscenter.identity.integration.mail;


import vn.ticketscenter.config.error.UpstreamException;

public class EmailDeliveryException extends UpstreamException {
    public EmailDeliveryException(String message) {
        super("EMAIL_DELIVERY_FAILED", message);
    }

    public EmailDeliveryException(String message, Throwable cause) {
        super("EMAIL_DELIVERY_FAILED", message, cause);
    }
}
