package vn.ticketscenter.identity.integration.mail;

public record EmailMessage(
        String recipient,
        String subject,
        String bodyText,
        String bodyHtml
) {
    public EmailMessage {
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("Email recipient must not be blank");
        }
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Email subject must not be blank");
        }
        if ((bodyText == null || bodyText.isBlank()) && (bodyHtml == null || bodyHtml.isBlank())) {
            throw new IllegalArgumentException("Email content must have either text or html body");
        }
    }
}
