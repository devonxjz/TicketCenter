package vn.ticketscenter.config;

/**
 * Cấu hình môi trường và máy chủ Web (Base URL, Môi trường, Secret Key).
 */
public final class ServerConfig {

    private ServerConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getBaseUrl() {
        return AppConfig.get("app.base.url", "http://localhost:8080/ticketscenter");
    }

    public static String getEnv() {
        return AppConfig.get("app.env", "development");
    }

    public static String getSecretKey() {
        return AppConfig.get("app.secret.key", "");
    }

    public static boolean isDevelopment() {
        return "development".equalsIgnoreCase(getEnv());
    }

    public static boolean isProduction() {
        return "production".equalsIgnoreCase(getEnv());
    }

    public static boolean isTest() {
        return "test".equalsIgnoreCase(getEnv());
    }
}
