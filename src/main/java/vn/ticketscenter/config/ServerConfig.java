package vn.ticketscenter.config;

/**
 * Web server and environment settings (base URL, environment, secret key).
 */
public final class ServerConfig {

    private ServerConfig() {
        // utility class
    }

    public static String getBaseUrl() {
        return AppConfig.get("app.base.url", "http://localhost:8080/ticketscenter");
    }

    public static String getEnv() {
        return AppConfig.get("app.env", "development");
    }

    public static boolean isProduction() {
        return "production".equalsIgnoreCase(getEnv());
    }

    public static boolean isDemoData() {
        return !isProduction() && "demo".equalsIgnoreCase(AppConfig.get("app.data.mode", "real"));
    }
}
