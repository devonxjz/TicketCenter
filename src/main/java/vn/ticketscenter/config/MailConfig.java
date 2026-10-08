package vn.ticketscenter.config;

/**
 * Cấu hình dịch vụ gửi email (SMTP).
 */
public final class MailConfig {

    private MailConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getHost() {
        return AppConfig.get("mail.smtp.host", "smtp.gmail.com");
    }

    public static int getPort() {
        return AppConfig.getInt("mail.smtp.port", 587);
    }

    public static String getUser() {
        return AppConfig.get("mail.smtp.user", "").trim();
    }

    public static String getPassword() {
        return AppConfig.get("mail.smtp.password", "").replaceAll("\\s+", "");
    }
}
