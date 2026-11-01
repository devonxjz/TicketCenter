package vn.ticketscenter.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads the configuration from a single file (application.properties / application.properties.example)
 * and lists every configuration key of the system in one place.
 *
 * A value is resolved in this order:
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
     * Every configuration key of the system, kept in one place so it is easy to find.
     */
    public static final class Keys {
        // 1. Microsoft SQL Server database
        public static final String DB_HOST = "db.host";
        public static final String DB_PORT = "db.port";
        public static final String DB_NAME = "db.name";
        public static final String DB_USER = "db.user";
        public static final String DB_PASSWORD = "db.password";
        public static final String DB_ENCRYPT = "db.encrypt";
        public static final String DB_TRUST_CERT = "db.trustServerCertificate";
        public static final String DB_TIMEOUT = "db.loginTimeout";

        // Aliases TC_SQL_*
        public static final String TC_SQL_HOST = "TC_SQL_HOST";
        public static final String TC_SQL_PORT = "TC_SQL_PORT";
        public static final String TC_SQL_TEST_DB = "TC_SQL_TEST_DB";
        public static final String TC_SQL_LOGIN = "TC_SQL_LOGIN";
        public static final String TC_SQL_PASSWORD = "TC_SQL_PASSWORD";
        public static final String TC_SQL_ENCRYPT = "TC_SQL_ENCRYPT";
        public static final String TC_SQL_TRUST_SERVER_CERT = "TC_SQL_TRUST_SERVER_CERT";
        public static final String TC_SQL_CONNECT_TIMEOUT_SEC = "TC_SQL_CONNECT_TIMEOUT_SEC";

        // 2. Web application
        public static final String APP_BASE_URL = "app.base.url";
        public static final String APP_SECRET_KEY = "app.secret.key";
        public static final String EVENT_IMAGE_DIRECTORY = "event.image.directory";
        public static final String REFUND_STATE_DIRECTORY = "refund.simulated.state.directory";
        public static final String REFUND_SIMULATED_OUTCOME = "refund.simulated.outcome";
        public static final String REFUND_TIMEOUT_AFTER_SIDE_EFFECT =
                "refund.simulated.timeoutAfterSideEffect";

        // 3. payOS payment gateway (environment variables PAYOS_CLIENT_ID, PAYOS_API_KEY, ...)
        public static final String PAYOS_CLIENT_ID = "payos.client.id";
        public static final String PAYOS_API_KEY = "payos.api.key";
        public static final String PAYOS_CHECKSUM_KEY = "payos.checksum.key";
        public static final String PAYOS_RETURN_URL = "payos.return.url";
        public static final String PAYOS_CANCEL_URL = "payos.cancel.url";
        public static final String PAYOS_LOG_LEVEL = "payos.log.level";

        // 5. Redis (idempotency and caching)
        public static final String REDIS_HOST = "redis.host";
        public static final String REDIS_PORT = "redis.port";
        public static final String REDIS_PASSWORD = "redis.password";
        public static final String REDIS_TIMEOUT = "redis.timeout";
        public static final String REDIS_URL = "redis.url";
        public static final String REDIS_CACHE_CATALOG_ENABLED = "redis.cache.catalog.enabled";

        // Identity (OTP, password reset, login throttling, session cache)
        public static final String ADMIN_EMAIL = "admin.email";
        public static final String IDENTITY_OTP_EXPIRY_MINUTES = "identity.otp.expiry.minutes";
        public static final String IDENTITY_OTP_COOLDOWN_SECONDS = "identity.otp.cooldown.seconds";
        public static final String IDENTITY_OTP_MAX_FAILED_ATTEMPTS =
                "identity.otp.max.failed.attempts";
        public static final String IDENTITY_RESET_TOKEN_EXPIRY_MINUTES =
                "identity.reset.token.expiry.minutes";
        public static final String IDENTITY_LOGIN_MAX_FAILURES = "identity.login.max.failures";
        public static final String IDENTITY_LOGIN_THROTTLE_WINDOW_MINUTES =
                "identity.login.throttle.window.minutes";
        public static final String IDENTITY_SESSION_CACHE_TTL_SECONDS =
                "identity.session.cache.ttl.seconds";

        private Keys() {
            // constants only
        }
    }

    static {
        loadProperties();
    }

    private AppConfig() {
        // utility class
    }

    private static void loadProperties() {
        ClassLoader classLoader = AppConfig.class.getClassLoader();

        // Private configuration is explicitly selected, outside Git and outside the WAR.
        String external = System.getProperty("tc.config.file");
        if (external == null || external.isBlank()) {
            external = System.getenv("TC_CONFIG_FILE");
        }
        if (external != null && !external.isBlank()) {
            try (InputStream input = Files.newInputStream(Path.of(external))) {
                PROPERTIES.load(input);
                return;
            } catch (IOException failure) {
                throw new IllegalStateException(
                        "Private application configuration could not be loaded");
            }
        }

        // 1. try the main configuration file, application.properties
        try (InputStream is = classLoader.getResourceAsStream(PROPERTIES_FILE)) {
            if (is != null) {
                PROPERTIES.load(is);
                return;
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file " + PROPERTIES_FILE, e);
        }

        // 2. fall back to application.properties.example when there is no local file (CI and
        // test runs)
        try (InputStream is = classLoader.getResourceAsStream(PROPERTIES_EXAMPLE_FILE)) {
            if (is != null) {
                PROPERTIES.load(is);
            } else {
                LOGGER.log(
                        Level.WARNING,
                        "Không tìm thấy file {0} hoặc {1} trong classpath",
                        new Object[] {PROPERTIES_FILE, PROPERTIES_EXAMPLE_FILE});
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Lỗi khi đọc file " + PROPERTIES_EXAMPLE_FILE, e);
        }
    }

    /**
     * The configured value for a key.
     *
     * @param key configuration key (for example "db.host" or "TC_SQL_HOST")
     * @return the value, or null when it is not set
     */
    public static String get(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }

        String mappedKey = resolveMappedKey(key);
        String[] candidates = {
            System.getProperty(key),
            System.getenv(key),
            System.getenv(key.replace('.', '_').toUpperCase()),
            System.getenv(key.replace(".", "").toUpperCase()),
            PROPERTIES.getProperty(key),
            mappedKey == null ? null : PROPERTIES.getProperty(mappedKey)
        };
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate.trim();
            }
        }
        return null;
    }

    /**
     * The configured value, or the default when it is not set.
     */
    public static String get(String key, String defaultValue) {
        String value = get(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }

    /**
     * The configured value as an int.
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
     * The configured value as a boolean.
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
