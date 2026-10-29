package vn.ticketscenter.config.payment;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import vn.payos.PayOS;
import vn.payos.core.ClientOptions;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.ServerConfig;

import java.time.Duration;
import java.util.Locale;

/**
 * payOS settings (in place of the {@code @Bean PayOS} of the Spring Boot demo).
 * Keys are read through {@link AppConfig}: system property, environment variable (PAYOS_CLIENT_ID, PAYOS_API_KEY,
 * PAYOS_CHECKSUM_KEY, ...), then application.properties.
 */
public record PayosConfig(
        String clientId,
        String apiKey,
        String checksumKey,
        String returnUrl,
        String cancelUrl,
        String logLevel) {
    public static final String RETURN_PATH = "/api/payments/payos/return";
    public static final String CANCEL_PATH = "/api/payments/payos/cancel";
    public static final String WEBHOOK_PATH = "/api/payments/payos/webhook";

    public PayosConfig {
        requireValue(clientId, AppConfig.Keys.PAYOS_CLIENT_ID);
        requireValue(apiKey, AppConfig.Keys.PAYOS_API_KEY);
        requireValue(checksumKey, AppConfig.Keys.PAYOS_CHECKSUM_KEY);
        requireValue(returnUrl, AppConfig.Keys.PAYOS_RETURN_URL);
        requireValue(cancelUrl, AppConfig.Keys.PAYOS_CANCEL_URL);
        logLevel =
                logLevel == null || logLevel.isBlank()
                        ? "NONE"
                        : logLevel.trim().toUpperCase(Locale.ROOT);
    }

    public static PayosConfig fromEnvironment() {
        String baseUrl = trimSlash(ServerConfig.getBaseUrl());
        return new PayosConfig(
                AppConfig.get(AppConfig.Keys.PAYOS_CLIENT_ID),
                AppConfig.get(AppConfig.Keys.PAYOS_API_KEY),
                AppConfig.get(AppConfig.Keys.PAYOS_CHECKSUM_KEY),
                AppConfig.get(
                        AppConfig.Keys.PAYOS_RETURN_URL,
                        baseUrl == null ? null : baseUrl + RETURN_PATH),
                AppConfig.get(
                        AppConfig.Keys.PAYOS_CANCEL_URL,
                        baseUrl == null ? null : baseUrl + CANCEL_PATH),
                AppConfig.get(AppConfig.Keys.PAYOS_LOG_LEVEL, "NONE"));
    }

    /** True when the three payOS credentials are present (URLs fall back to app.base.url). */
    public static boolean isConfigured() {
        return present(AppConfig.get(AppConfig.Keys.PAYOS_CLIENT_ID))
                && present(AppConfig.get(AppConfig.Keys.PAYOS_API_KEY))
                && present(AppConfig.get(AppConfig.Keys.PAYOS_CHECKSUM_KEY));
    }

    /**
     * The HTTP client is created here (not inside the SDK) so it can be shut down with the web app:
     * the SDK never closes a client it built itself, and its idle threads outlive a redeploy.
     */
    public OkHttpClient httpClient() {
        Duration timeout = Duration.ofSeconds(15);
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .connectTimeout(timeout)
                        .readTimeout(timeout)
                        .writeTimeout(timeout);
        if (!"NONE".equals(logLevel)) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(
                    "DEBUG".equals(logLevel)
                            ? HttpLoggingInterceptor.Level.BODY
                            : HttpLoggingInterceptor.Level.BASIC);
            for (String secret :
                    new String[] {
                        "Authorization", "Cookie", "Set-Cookie", "x-client-id", "x-api-key"
                    }) {
                logging.redactHeader(secret);
            }
            builder.addInterceptor(logging);
        }
        return builder.build();
    }

    public PayOS client(OkHttpClient http) {
        return new PayOS(
                ClientOptions.builder()
                        .clientId(clientId)
                        .apiKey(apiKey)
                        .checksumKey(checksumKey)
                        .logLevel(ClientOptions.LogLevel.valueOf(logLevel))
                        .httpClient(http)
                        .build());
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank() && !value.startsWith("<");
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void requireValue(String value, String name) {
        if (!present(value)) {
            throw new IllegalStateException(name + " is not configured");
        }
    }
}
