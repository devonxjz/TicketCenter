package vn.ticketscenter.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý nạp cấu hình tập trung từ 1 file duy nhất (application.properties / application.properties.example)
 * và tổng hợp toàn bộ các khóa cấu hình (keys) của toàn hệ thống trong một file duy nhất.
 *
 * Thứ tự ưu tiên giải quyết giá trị:
 * 1. System Properties (-Dkey=value)
 * 2. Environment Variables (VD: TC_SQL_HOST, DB_HOST)
 * 3. File application.properties trong classpath
 */
public final class AppConfig {

    private static final Logger LOGGER = Logger.getLogger(AppConfig.class.getName());
    private static final String PROPERTIES_FILE = "application.properties";
    private static final String PROPERTIES_EXAMPLE_FILE = "application.properties.example";

    private static final Properties PROPERTIES = new Properties();

    /**
     * Tập trung toàn bộ key cấu hình của hệ thống vào 1 nơi duy nhất để tiện quản lý và tra cứu.
     */
    public static final class Keys {
        // 1. Cơ sở dữ liệu Microsoft SQL Server
        public static final String DB_HOST = "db.host";
        public static final String DB_PORT = "db.port";
        public static final String DB_NAME = "db.name";
        public static final String DB_USER = "db.user";
        public static final String DB_PASSWORD = "db.password";
        public static final String DB_ENCRYPT = "db.encrypt";
        public static final String DB_TRUST_CERT = "db.trustServerCertificate";
        public static final String DB_TIMEOUT = "db.loginTimeout";
        public static final String DB_URL = "db.url";

        // Aliases TC_SQL_*
        public static final String TC_SQL_HOST = "TC_SQL_HOST";
        public static final String TC_SQL_PORT = "TC_SQL_PORT";
        public static final String TC_SQL_DEV_DB = "TC_SQL_DEV_DB";
        public static final String TC_SQL_TEST_DB = "TC_SQL_TEST_DB";
        public static final String TC_SQL_BENCH_DB = "TC_SQL_BENCH_DB";
        public static final String TC_SQL_LOGIN = "TC_SQL_LOGIN";
        public static final String TC_SQL_PASSWORD = "TC_SQL_PASSWORD";
        public static final String TC_SQL_ENCRYPT = "TC_SQL_ENCRYPT";
        public static final String TC_SQL_TRUST_SERVER_CERT = "TC_SQL_TRUST_SERVER_CERT";
        public static final String TC_SQL_CONNECT_TIMEOUT_SEC = "TC_SQL_CONNECT_TIMEOUT_SEC";

        // 2. Cấu hình Ứng dụng Web
        public static final String APP_BASE_URL = "app.base.url";
        public static final String APP_ENV = "app.env";
        public static final String APP_SECRET_KEY = "app.secret.key";
        public static final String EVENT_IMAGE_DIRECTORY = "event.image.directory";
        public static final String REFUND_STATE_DIRECTORY = "refund.simulated.state.directory";
        public static final String REFUND_SIMULATED_OUTCOME = "refund.simulated.outcome";
        public static final String REFUND_TIMEOUT_AFTER_SIDE_EFFECT = "refund.simulated.timeoutAfterSideEffect";
        public static final String PAYOUT_STATE_DIRECTORY = "payout.simulated.state.directory";
        public static final String PAYOUT_SIMULATED_OUTCOME = "payout.simulated.outcome";
        public static final String PAYOUT_TIMEOUT_AFTER_SIDE_EFFECT = "payout.simulated.timeoutAfterSideEffect";

        // 3. Cổng Thanh Toán payOS (biến môi trường tương ứng: PAYOS_CLIENT_ID, PAYOS_API_KEY, ...)
        public static final String PAYOS_CLIENT_ID = "payos.client.id";
        public static final String PAYOS_API_KEY = "payos.api.key";
        public static final String PAYOS_CHECKSUM_KEY = "payos.checksum.key";
        public static final String PAYOS_RETURN_URL = "payos.return.url";
        public static final String PAYOS_CANCEL_URL = "payos.cancel.url";
        public static final String PAYOS_LOG_LEVEL = "payos.log.level";

        // 4. Dịch vụ Gửi Email (SMTP)
        public static final String MAIL_HOST = "mail.smtp.host";
        public static final String MAIL_PORT = "mail.smtp.port";
        public static final String MAIL_USER = "mail.smtp.user";
        public static final String MAIL_PASSWORD = "mail.smtp.password";

        // 5. Cấu hình Redis (Idempotency & Caching)
        public static final String REDIS_HOST = "redis.host";
        public static final String REDIS_PORT = "redis.port";
        public static final String REDIS_PASSWORD = "redis.password";
        public static final String REDIS_TIMEOUT = "redis.timeout";
        public static final String REDIS_URL = "redis.url";
        public static final String REDIS_CACHE_CATALOG_ENABLED = "redis.cache.catalog.enabled";

        // Identity (OTP, đặt lại mật khẩu, chống dò mật khẩu, cache phiên)
        public static final String ADMIN_EMAIL = "admin.email";
        public static final String IDENTITY_OTP_EXPIRY_MINUTES = "identity.otp.expiry.minutes";
        public static final String IDENTITY_OTP_COOLDOWN_SECONDS = "identity.otp.cooldown.seconds";
        public static final String IDENTITY_OTP_MAX_FAILED_ATTEMPTS = "identity.otp.max.failed.attempts";
        public static final String IDENTITY_RESET_TOKEN_EXPIRY_MINUTES = "identity.reset.token.expiry.minutes";
        public static final String IDENTITY_LOGIN_MAX_FAILURES = "identity.login.max.failures";
        public static final String IDENTITY_LOGIN_THROTTLE_WINDOW_MINUTES = "identity.login.throttle.window.minutes";
        public static final String IDENTITY_SESSION_CACHE_TTL_SECONDS = "identity.session.cache.ttl.seconds";

        // 6. Cấu hình Connection Pool (HikariCP)
        public static final String HIKARI_MAX_POOL_SIZE = "hikari.maximum-pool-size";
        public static final String HIKARI_MIN_IDLE = "hikari.minimum-idle";
        public static final String HIKARI_IDLE_TIMEOUT = "hikari.idle-timeout";
        public static final String HIKARI_CONNECTION_TIMEOUT = "hikari.connection-timeout";
        public static final String HIKARI_MAX_LIFETIME = "hikari.max-lifetime";
        public static final String HIKARI_POOL_BUDGET = "hikari.pool-budget";

        private Keys() {
            // Không cho phép khởi tạo hằng số keys
        }
    }

    static {
        loadProperties();
    }

    private AppConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    private static void loadProperties() {
        ClassLoader classLoader = AppConfig.class.getClassLoader();

        // Private configuration is explicitly selected, outside Git and outside the WAR.
        String external = System.getProperty("tc.config.file");
        if (external == null || external.isBlank()) external = System.getenv("TC_CONFIG_FILE");
        if (external != null && !external.isBlank()) {
            try (InputStream input = Files.newInputStream(Path.of(external))) {
                PROPERTIES.load(input);
                return;
            } catch (IOException failure) {
                throw new IllegalStateException("Private application configuration could not be loaded");
            }
        }

        // 1. Thử nạp từ file cấu hình chính application.properties
        try (InputStream is = classLoader.getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                PROPERTIES.load(is);
                return;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file " + PROPERTIES_FILE, e);
        }

        // 2. Fallback nạp từ application.properties.example nếu chưa có file cấu hình cục bộ (dành cho CI và Test runner)
        try (InputStream is = classLoader.getResourceAsStream(PROPERTIES_EXAMPLE_FILE)) {
            if (is != null) {
                PROPERTIES.load(is);
            } else {
                LOGGER.log(Level.WARNING, "Không tìm thấy file {0} hoặc {1} trong classpath",
                        new Object[]{PROPERTIES_FILE, PROPERTIES_EXAMPLE_FILE});
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file " + PROPERTIES_EXAMPLE_FILE, e);
        }
    }

    /**
     * Lấy giá trị cấu hình theo key.
     *
     * @param key Khóa cấu hình (ví dụ: "db.host" hoặc "TC_SQL_HOST")
     * @return Giá trị cấu hình hoặc null nếu không tồn tại
     */
    public static String get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        // 1. Kiểm tra System Property
        String sysProp = System.getProperty(key);
        if (sysProp != null && !sysProp.isBlank()) {
            return sysProp.trim();
        }

        // 2. Kiểm tra System Environment
        String envValue = System.getenv(key);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }

        String normalizedEnvKey = key.replace('.', '_').toUpperCase();
        String normalizedEnvVal = System.getenv(normalizedEnvKey);
        if (normalizedEnvVal != null && !normalizedEnvVal.isBlank()) {
            return normalizedEnvVal.trim();
        }

        // 3. Fallback cho Railway: các biến môi trường không có dấu gạch dưới (REDISHOST, REDISPORT, REDISPASSWORD, REDISURL)
        String compactEnvKey = key.replace(".", "").toUpperCase();
        String compactEnvVal = System.getenv(compactEnvKey);
        if (compactEnvVal != null && !compactEnvVal.isBlank()) {
            return compactEnvVal.trim();
        }

        // 4. Kiểm tra Properties file
        String propValue = PROPERTIES.getProperty(key);
        if (propValue != null && !propValue.isBlank()) {
            return propValue.trim();
        }

        // 4. Fallback lookup giữa dạng dot notation và TC_SQL_* notation
        String mappedKey = resolveMappedKey(key);
        if (mappedKey != null) {
            String mappedValue = PROPERTIES.getProperty(mappedKey);
            if (mappedValue != null && !mappedValue.isBlank()) {
                return mappedValue.trim();
            }
        }

        return null;
    }

    /**
     * Lấy giá trị cấu hình với giá trị mặc định.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * Lấy giá trị cấu hình kiểu int.
     */
    public static int getInt(String key, int defaultValue) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Lấy giá trị cấu hình kiểu boolean.
     */
    public static boolean getBoolean(String key, boolean defaultValue) {
        String val = get(key);
        if (val == null || val.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(val.trim());
    }

    private static String resolveMappedKey(String key) {
        return switch (key) {
            case "TC_SQL_HOST" -> Keys.DB_HOST;
            case "TC_SQL_PORT" -> Keys.DB_PORT;
            case "TC_SQL_DEV_DB", "TC_SQL_TEST_DB", "TC_SQL_BENCH_DB" -> Keys.DB_NAME;
            case "TC_SQL_LOGIN" -> Keys.DB_USER;
            case "TC_SQL_PASSWORD" -> Keys.DB_PASSWORD;
            case "TC_SQL_ENCRYPT" -> Keys.DB_ENCRYPT;
            case "TC_SQL_TRUST_SERVER_CERT" -> Keys.DB_TRUST_CERT;
            case "TC_SQL_CONNECT_TIMEOUT_SEC" -> Keys.DB_TIMEOUT;
            case "db.host" -> Keys.TC_SQL_HOST;
            case "db.port" -> Keys.TC_SQL_PORT;
            case "db.name" -> Keys.TC_SQL_TEST_DB;
            case "db.user" -> Keys.TC_SQL_LOGIN;
            case "db.password" -> Keys.TC_SQL_PASSWORD;
            case "db.encrypt" -> Keys.TC_SQL_ENCRYPT;
            case "db.trustServerCertificate" -> Keys.TC_SQL_TRUST_SERVER_CERT;
            case "db.loginTimeout" -> Keys.TC_SQL_CONNECT_TIMEOUT_SEC;
            default -> null;
        };
    }
}
