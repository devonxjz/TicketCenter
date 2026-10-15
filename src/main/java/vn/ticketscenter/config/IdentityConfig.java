package vn.ticketscenter.config;

/**
 * Cấu hình của module identity (OTP, đặt lại mật khẩu, chống dò mật khẩu, cache phiên, tài khoản admin).
 * Mọi giá trị đọc qua {@link AppConfig}; mặc định giữ nguyên hành vi cũ.
 */
public final class IdentityConfig {

    private IdentityConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static int getOtpExpiryMinutes() {
        return positive(AppConfig.Keys.IDENTITY_OTP_EXPIRY_MINUTES, 5);
    }

    public static int getOtpCooldownSeconds() {
        return positive(AppConfig.Keys.IDENTITY_OTP_COOLDOWN_SECONDS, 60);
    }

    public static int getOtpMaxFailedAttempts() {
        return positive(AppConfig.Keys.IDENTITY_OTP_MAX_FAILED_ATTEMPTS, 5);
    }

    public static int getResetTokenExpiryMinutes() {
        return positive(AppConfig.Keys.IDENTITY_RESET_TOKEN_EXPIRY_MINUTES, 15);
    }

    public static int getLoginMaxFailures() {
        return positive(AppConfig.Keys.IDENTITY_LOGIN_MAX_FAILURES, 5);
    }

    public static int getLoginThrottleWindowMinutes() {
        return positive(AppConfig.Keys.IDENTITY_LOGIN_THROTTLE_WINDOW_MINUTES, 15);
    }

    public static int getSessionCacheTtlSeconds() {
        return positive(AppConfig.Keys.IDENTITY_SESSION_CACHE_TTL_SECONDS, 180);
    }

    public static String getAdminEmail() {
        return AppConfig.get(AppConfig.Keys.ADMIN_EMAIL, "admin@fixture.invalid");
    }

    private static int positive(String key, int defaultValue) {
        int value = AppConfig.getInt(key, defaultValue);
        return value > 0 ? value : defaultValue;
    }
}
