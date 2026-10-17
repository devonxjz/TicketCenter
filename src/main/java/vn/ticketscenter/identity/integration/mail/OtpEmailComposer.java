package vn.ticketscenter.identity.integration.mail;

import vn.ticketscenter.identity.model.OtpPurpose;

/** Builds the text and HTML versions of the email that carries an OTP. */
public final class OtpEmailComposer {

    private static final String SUBJECT_PREFIX = "[TicketsCenter] ";

    private OtpEmailComposer() {}

    public static EmailMessage compose(
            String recipient,
            OtpPurpose purpose,
            String fullName,
            String otpCode,
            int expiryMinutes) {
        boolean verifyEmail = purpose == OtpPurpose.VERIFY_EMAIL;
        String title = verifyEmail ? "Xác minh địa chỉ email" : "Yêu cầu đặt lại mật khẩu";
        String action =
                verifyEmail
                        ? "xác minh địa chỉ email của bạn"
                        : "đặt lại mật khẩu tài khoản của bạn";

        String text =
                String.format(
                        "Xin chào %s,\n\nMã xác thực OTP để %s là:\n\n    %s\n\n"
                                + "Mã này có hiệu lực trong vòng %d phút. "
                                + "Vì lý do bảo mật, tuyệt đối không chia sẻ mã này với bất kỳ ai.\n"
                                + "Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.\n\n"
                                + "Trân trọng,\nĐội ngũ TicketsCenter",
                        fullName, action, otpCode, expiryMinutes);
        String html = OtpEmailTemplate.render(title, fullName, action, otpCode, expiryMinutes);
        return new EmailMessage(recipient, SUBJECT_PREFIX + title, text, html);
    }
}
