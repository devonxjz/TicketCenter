package vn.ticketscenter.identity.integration.mail;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeEmailSender implements EmailSender {

    private final List<EmailMessage> sentMessages = new ArrayList<>();
    private boolean simulateFailure = false;

    @Override
    public synchronized void send(EmailMessage message) throws EmailDeliveryException {
        if (simulateFailure) {
            throw new EmailDeliveryException("Simulated SMTP delivery failure");
        }
        sentMessages.add(message);
    }

    public synchronized List<EmailMessage> getSentMessages() {
        return Collections.unmodifiableList(new ArrayList<>(sentMessages));
    }

    public synchronized void setSimulateFailure(boolean simulateFailure) {
        this.simulateFailure = simulateFailure;
    }

    public synchronized void clear() {
        sentMessages.clear();
        simulateFailure = false;
    }
}
