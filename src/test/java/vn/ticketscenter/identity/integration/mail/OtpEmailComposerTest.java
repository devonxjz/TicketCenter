package vn.ticketscenter.identity.integration.mail;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.OtpPurpose;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OtpEmailComposerTest {

    @Test
    void verifyEmailMessageCarriesCodeInTextAndHtml() {
        EmailMessage message = OtpEmailComposer.compose(
                "user@test.vn", OtpPurpose.VERIFY_EMAIL, "Nguyen Van A", "123456", 5);

        assertEquals("user@test.vn", message.recipient());
        assertEquals("[TicketsCenter] Xác minh địa chỉ email", message.subject());
        assertTrue(message.bodyText().contains("Xin chào Nguyen Van A,"));
        assertTrue(message.bodyText().contains("xác minh địa chỉ email của bạn"));
        assertTrue(message.bodyText().contains("    123456\n"));
        assertTrue(message.bodyText().contains("trong vòng 5 phút"));
        assertTrue(message.bodyHtml().contains("123456"));
    }

    @Test
    void resetPasswordUsesItsOwnSubjectAndEscapesTheNameInHtml() {
        EmailMessage message = OtpEmailComposer.compose(
                "user@test.vn", OtpPurpose.RESET_PASSWORD, "<b>Eve</b>", "654321", 5);

        assertEquals("[TicketsCenter] Yêu cầu đặt lại mật khẩu", message.subject());
        assertTrue(message.bodyText().contains("đặt lại mật khẩu tài khoản của bạn"));
        assertFalse(message.bodyHtml().contains("<b>Eve</b>"));
        assertTrue(message.bodyHtml().contains("&lt;b&gt;Eve&lt;/b&gt;"));
    }
}
