package vn.ticketscenter.identity.integration.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
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
    @DisplayName("EmailSenderImpl dựng multipart/alternative base64 khi có cả text và html")
    void smtpEmailSenderShouldBuildMultipartAlternative() {
        String html = OtpEmailTemplate.render("Xác minh địa chỉ email", "<b>Thái</b>", "xác minh email", "123456", 5);
        assertTrue(html.contains("123456"));
        assertTrue(html.contains("&lt;b&gt;Thái&lt;/b&gt;"), "Tên người dùng phải được escape HTML");

        String mime = EmailSenderImpl.buildMimeMessage("sender@fixture.invalid",
                new EmailMessage("user@test.com", "Tiêu đề", "Mã: 123456\n.\nHết", html));

        assertTrue(mime.contains("Content-Type: multipart/alternative; boundary="));
        assertTrue(mime.contains("Content-Type: text/plain; charset=UTF-8"));
        assertTrue(mime.contains("Content-Type: text/html; charset=UTF-8"));
        for (String line : mime.split("\r\n", -1)) {
            assertTrue(line.length() <= 998, "Dòng vượt giới hạn SMTP");
            assertFalse(line.startsWith("."), "Không được có dòng bắt đầu bằng dấu chấm trong DATA");
            assertFalse(line.contains("\n"), "Không được có LF trần");
        }

        String plainOnly = EmailSenderImpl.buildMimeMessage("sender@fixture.invalid",
                new EmailMessage("user@test.com", "Tiêu đề", "Mã: 123456", null));
        assertFalse(plainOnly.contains("multipart/alternative"));
        assertTrue(plainOnly.contains("Content-Type: text/plain; charset=UTF-8"));
    }

    @Test
    @DisplayName("EmailSenderImpl gửi email thật qua Gmail SMTP")
    void smtpEmailSenderShouldSendRealEmail() {
        EmailSenderImpl sender = new EmailSenderImpl();
        EmailMessage message = new EmailMessage(
                "recipient@fixture.invalid",
                "[TicketsCenter] Kiểm tra mã OTP thực tế",
                "Xin chào,\n\nĐây là mã OTP kiểm thử thực tế từ TicketsCenter: 889966\nHiệu lực: 5 phút.",
                null
        );
        assertDoesNotThrow(() -> sender.send(message));
    }
}
