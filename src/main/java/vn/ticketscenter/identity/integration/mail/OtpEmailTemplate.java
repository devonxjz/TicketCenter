package vn.ticketscenter.identity.integration.mail;

/**
 * HTML template of the OTP email. It uses tables and inline styles because mail clients
 * (Gmail, Outlook) ignore style tags and flex/grid layout.
 */
public final class OtpEmailTemplate {

    private static final String TEMPLATE =
            """
            <!DOCTYPE html>
            <html lang="vi">
            <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
            <body style="margin:0;padding:0;background-color:#f1f5f9;">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="background-color:#f1f5f9;">
              <tr><td align="center" style="padding:32px 12px;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" border="0" style="max-width:520px;background-color:#ffffff;border-radius:14px;overflow:hidden;border:1px solid #e2e8f0;">
                  <tr><td align="center" bgcolor="#e11d48" style="background-color:#e11d48;background-image:linear-gradient(135deg,#e11d48,#be123c);padding:28px 24px;">
                    <div style="font-family:Arial,Helvetica,sans-serif;font-size:24px;font-weight:bold;letter-spacing:.5px;color:#ffffff;">TicketsCenter</div>
                    <div style="font-family:Arial,Helvetica,sans-serif;font-size:13px;color:#ffe4e6;padding-top:6px;">{{TITLE}}</div>
                  </td></tr>
                  <tr><td style="padding:32px 32px 8px 32px;font-family:Arial,Helvetica,sans-serif;color:#0f172a;">
                    <p style="margin:0 0 12px 0;font-size:16px;">Xin chào <strong>{{NAME}}</strong>,</p>
                    <p style="margin:0;font-size:15px;line-height:1.6;color:#334155;">Mã xác thực OTP để {{ACTION}} là:</p>
                  </td></tr>
                  <tr><td align="center" style="padding:16px 32px;">
                    <div style="background-color:#fff1f2;border:1px dashed #e11d48;border-radius:10px;padding:18px 12px;font-family:'Courier New',Courier,monospace;font-size:36px;font-weight:bold;letter-spacing:10px;color:#be123c;">{{CODE}}</div>
                  </td></tr>
                  <tr><td style="padding:8px 32px 28px 32px;font-family:Arial,Helvetica,sans-serif;">
                    <p style="margin:0 0 14px 0;font-size:14px;line-height:1.6;color:#334155;">Mã có hiệu lực trong <strong style="color:#0f172a;">{{MINUTES}} phút</strong>.</p>
                    <div style="background-color:#f0f9ff;border-left:4px solid #0284c7;border-radius:4px;padding:12px 14px;font-size:13px;line-height:1.6;color:#0f172a;">
                      Vì lý do bảo mật, tuyệt đối không chia sẻ mã này với bất kỳ ai. Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.
                    </div>
                  </td></tr>
                  <tr><td align="center" bgcolor="#0f172a" style="background-color:#0f172a;padding:18px 24px;font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.6;color:#94a3b8;">
                    Trân trọng, Đội ngũ TicketsCenter<br>Email này được gửi tự động, vui lòng không trả lời.
                  </td></tr>
                </table>
              </td></tr>
            </table>
            </body>
            </html>
            """;

    private OtpEmailTemplate() {
        // utility class
    }

    public static String render(
            String title,
            String fullName,
            String purposeAction,
            String otpCode,
            int expiryMinutes) {
        return TEMPLATE.replace("{{TITLE}}", escape(title))
                .replace("{{ACTION}}", escape(purposeAction))
                .replace("{{CODE}}", escape(otpCode))
                .replace("{{MINUTES}}", String.valueOf(expiryMinutes))
                .replace("{{NAME}}", escape(fullName));
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
