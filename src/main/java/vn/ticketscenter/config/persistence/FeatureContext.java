package vn.ticketscenter.config.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Servlet replacement for Spring beans: each feature builds its service once per {@link ServletContext},
 * keeps the pools it opened beside it and closes them with the web application.
 */
public final class FeatureContext {
    private FeatureContext() {}

    /** Pools and clients a feature opened while it was built; closed together. */
    public static final class Resources {
        private final List<AutoCloseable> opened = new ArrayList<>();

        public <R extends AutoCloseable> R track(R resource) {
            opened.add(resource);
            return resource;
        }

        /** One-connection pool that only uses the principal's own login (db.&lt;principal&gt;.user). */
        public HikariDataSource strictPool(String name, DatabasePrincipal principal) {
            String prefix = "db." + principal.name().toLowerCase(Locale.ROOT);
            String user = AppConfig.get(prefix + ".user");
            String password = AppConfig.get(prefix + ".password");
            if (user == null || user.isBlank() || password == null || password.isBlank())
                throw new IllegalStateException(prefix + ".user/password are required");
            return pool(name, user, password, true, 10_000);
        }

        /** One-connection pool that falls back to db.user when the principal has no login of its own. */
        public HikariDataSource fallbackPool(
                String name, DatabasePrincipal principal, boolean autoCommit, int timeoutMillis) {
            return pool(
                    name,
                    DatabaseConfig.getPrincipalUser(principal),
                    DatabaseConfig.getPrincipalPassword(principal),
                    autoCommit,
                    timeoutMillis);
        }

        public List<AutoCloseable> opened() {
            return List.copyOf(opened);
        }

        private HikariDataSource pool(
                String name, String user, String password, boolean autoCommit, int timeoutMillis) {
            HikariConfig config = new HikariConfig();
            config.setPoolName(name);
            config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
            config.setUsername(user);
            config.setPassword(password);
            config.setMaximumPoolSize(1);
            config.setMinimumIdle(0);
            config.setInitializationFailTimeout(-1);
            config.setConnectionTimeout(timeoutMillis);
            config.setAutoCommit(autoCommit);
            return track(new HikariDataSource(config));
        }

        private void close() {
            for (AutoCloseable resource : opened.reversed()) {
                try {
                    resource.close();
                } catch (Exception ignored) {
                    // the application is stopping; the remaining resources must still close
                }
            }
        }
    }

    /**
     * Returns the service stored under {@code key}, building it on first use. A failed build closes what it
     * opened and is rethrown through {@code onFailure}; after {@link #close} the feature stays unavailable.
     */
    public static <T> T resolve(
            ServletContext context,
            String key,
            Function<Resources, T> builder,
            Function<RuntimeException, RuntimeException> onFailure) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(key + ".closed")))
                throw onFailure.apply(new IllegalStateException(key + " is closed"));
            Object existing = context.getAttribute(key);
            if (existing != null) return cast(existing);
            Resources resources = new Resources();
            try {
                T service = builder.apply(resources);
                context.setAttribute(key + ".resources", resources);
                context.setAttribute(key, service);
                return service;
            } catch (RuntimeException failure) {
                resources.close();
                throw onFailure.apply(failure);
            }
        }
    }

    /** Lazily stores a value that owns no pools of its own, such as services on the shared registry. */
    public static <T> T cached(ServletContext context, String key, Supplier<T> builder) {
        synchronized (context) {
            Object existing = context.getAttribute(key);
            if (existing != null) return cast(existing);
            T value = builder.get();
            context.setAttribute(key, value);
            return value;
        }
    }

    /** The shared registry opened by {@link PersistenceListener}, if it started. */
    public static Optional<PersistenceRegistry> registry(ServletContext context) {
        return context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE) instanceof PersistenceRegistry registry
                ? Optional.of(registry)
                : Optional.empty();
    }

    /** The shared registry, provided it has a login for {@code principal} (any registry when null). */
    public static PersistenceRegistry registry(
            ServletContext context, DatabasePrincipal principal, Supplier<RuntimeException> unavailable) {
        return registry(context)
                .filter(registry -> principal == null || registry.hasPrincipal(principal))
                .orElseThrow(unavailable);
    }

    /** What the feature under {@code key} opened; empty before it is built. */
    public static List<AutoCloseable> resources(ServletContext context, String key) {
        return context.getAttribute(key + ".resources") instanceof Resources resources
                ? resources.opened()
                : List.of();
    }

    public static void open(ServletContext context, String key) {
        context.setAttribute(key + ".closed", false);
    }

    public static void close(ServletContext context, String key) {
        synchronized (context) {
            context.setAttribute(key + ".closed", true);
            if (context.getAttribute(key + ".resources") instanceof Resources resources) resources.close();
            context.removeAttribute(key + ".resources");
            context.removeAttribute(key);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object value) {
        return (T) value;
    }
}
