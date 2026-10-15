package vn.ticketscenter.identity.integration.mail;

public interface EmailSender {
    void send(EmailMessage message) throws EmailDeliveryException;
}
