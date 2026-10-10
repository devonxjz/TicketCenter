package vn.ticketscenter.identity.integration.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class EmailSenderTest {

    @Test
    @DisplayName("EmailMessage validate recipient, subject, và content không được để trống")
    void shouldValidateEmailMessageFields() {
        assertThrows(IllegalArgumentException.class, () -> new EmailMessage("", "Subject", "Body", null));
        assertThrows(IllegalArgumentException.class, () -> new EmailMessage("user@test.com", "   ", "Body", null));
        assertThrows(IllegalArgumentException.class, () -> new EmailMessage("user@test.com", "Subject", "  ", "   "));

        EmailMessage msg = new EmailMessage("user@test.com", "Subject", "Hello", "<p>Hello</p>");
        assertEquals("user@test.com", msg.recipient());
        assertEquals("Subject", msg.subject());
        assertEquals("Hello", msg.bodyText());
        assertEquals("<p>Hello</p>", msg.bodyHtml());
    }

    @Test
    @DisplayName("FakeEmailSender gửi thư thành công và lưu vào danh sách đã gửi")
    void fakeEmailSenderShouldRecordMessages() throws EmailDeliveryException {
        FakeEmailSender sender = new FakeEmailSender();
        EmailMessage message = new EmailMessage("test@example.com", "Test OTP", "Mã: 123456", null);

        sender.send(message);

        assertEquals(1, sender.getSentMessages().size());
        assertEquals("test@example.com", sender.getSentMessages().get(0).recipient());
        assertEquals("Test OTP", sender.getSentMessages().get(0).subject());

        sender.clear();
        assertTrue(sender.getSentMessages().isEmpty());
    }

    @Test
    @DisplayName("FakeEmailSender ném EmailDeliveryException khi simulateFailure được bật")
    void fakeEmailSenderShouldThrowWhenFailureSimulated() {
        FakeEmailSender sender = new FakeEmailSender();
        sender.setSimulateFailure(true);
        EmailMessage message = new EmailMessage("test@example.com", "Test OTP", "Mã: 123456", null);

        assertThrows(EmailDeliveryException.class, () -> sender.send(message));
        assertTrue(sender.getSentMessages().isEmpty());
    }

    @Test
    @DisplayName("SmtpEmailSender gửi email thật qua Gmail SMTP")
    void smtpEmailSenderShouldSendRealEmail() {
        SmtpEmailSender sender = new SmtpEmailSender();
        EmailMessage message = new EmailMessage(
                "tranlethai11102006@gmail.com",
                "[TicketsCenter] Kiểm tra mã OTP thực tế",
                "Xin chào,\n\nĐây là mã OTP kiểm thử thực tế từ TicketsCenter: 889966\nHiệu lực: 5 phút.",
                null
        );
        assertDoesNotThrow(() -> sender.send(message));
    }
}
