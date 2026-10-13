package vn.ticketscenter.event.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;

import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.storage.CloudinaryStorageService;
import vn.ticketscenter.storage.StorageService;

import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

@WebListener
public final class EventRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = EventService.class.getName();
    private static final String POOLS_ATTRIBUTE = EventRuntime.class.getName() + ".pools";
    private static final Logger LOG = Logger.getLogger(EventRuntime.class.getName());
    private ScheduledExecutorService cleanup;

    public static EventService resolve(ServletContext context) {
        synchronized (context) {
            Object existing = context.getAttribute(SERVICE_ATTRIBUTE);
            if (existing instanceof EventService service) return service;
            List<HikariDataSource> pools = new ArrayList<>();
            try {
                HikariDataSource manager = pool(DatabasePrincipal.MANAGER);
                pools.add(manager);
                HikariDataSource buyer = pool(DatabasePrincipal.BUYER);
                pools.add(buyer);
                HikariDataSource worker = pool(DatabasePrincipal.WORKER);
                pools.add(worker);
                StorageService storage = resolveStorage();
                vn.ticketscenter.event.cache.EventIdempotencyStore redis = resolveRedis();
                EventService service =
                        new EventService(
                                new EventRepository(
                                        ConnectionBudgets.wrap(manager),
                                        ConnectionBudgets.wrap(buyer),
                                        ConnectionBudgets.wrap(worker)),
                                storage,
                                redis);
                context.setAttribute(POOLS_ATTRIBUTE, pools);
                context.setAttribute(SERVICE_ATTRIBUTE, service);
                return service;
            } catch (RuntimeException e) {
                pools.forEach(HikariDataSource::close);
                throw e;
            }
        }
    }

    private static StorageService resolveStorage() {
        String cloudName = vn.ticketscenter.config.AppConfig.get(vn.ticketscenter.config.AppConfig.Keys.CLOUDINARY_CLOUD_NAME);
        if (cloudName != null && !cloudName.isBlank()) {
            return CloudinaryStorageService.fromEnvironment();
        }
        return new LocalSimulatedStorage();
    }

    private static vn.ticketscenter.event.cache.EventIdempotencyStore resolveRedis() {
        if (vn.ticketscenter.config.AppConfig.getBoolean("event.redis.enabled", false)) {
            return new vn.ticketscenter.event.cache.RedisEventIdempotencyStore();
        }
        return new vn.ticketscenter.event.cache.InMemoryEventIdempotencyStore();
    }

    public static final class LocalSimulatedStorage implements vn.ticketscenter.storage.StorageService {
        private final Map<String, vn.ticketscenter.storage.StoredAsset> assets = new ConcurrentHashMap<>();

        @Override
        public vn.ticketscenter.storage.StoredAsset upload(java.io.InputStream content, String folder) throws java.io.IOException {
            return ensure(content, folder + "/" + UUID.randomUUID());
        }

        @Override
        public vn.ticketscenter.storage.StoredAsset ensure(java.io.InputStream content, String immutablePublicId) throws java.io.IOException {
            byte[] bytes = vn.ticketscenter.storage.ImageContentValidator.readAndValidate(content);
            String dir = vn.ticketscenter.config.AppConfig.get(vn.ticketscenter.config.AppConfig.Keys.EVENT_IMAGE_DIRECTORY);
            if (dir != null && !dir.isBlank()) {
                try {
                    java.nio.file.Path file = java.nio.file.Path.of(dir, immutablePublicId.replace('/', '_') + ".png");
                    java.nio.file.Files.createDirectories(file.getParent());
                    java.nio.file.Files.write(file, bytes);
                } catch (Exception ignored) {}
            }
            vn.ticketscenter.storage.StoredAsset asset = new vn.ticketscenter.storage.StoredAsset(
                    "https://res.cloudinary.com/simulated/image/upload/" + immutablePublicId + ".png",
                    immutablePublicId);
            assets.put(immutablePublicId, asset);
            return asset;
        }

        @Override
        public Optional<vn.ticketscenter.storage.StoredAsset> find(String publicId) {
            return Optional.ofNullable(assets.get(publicId));
        }

        @Override
        public void delete(String publicId) {
            assets.remove(publicId);
        }

        @Override
        public String getUrl(String publicId) {
            return "https://res.cloudinary.com/simulated/image/upload/" + publicId + ".png";
        }
    }

    private static HikariDataSource pool(DatabasePrincipal principal) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("event-images-" + principal.name().toLowerCase(Locale.ROOT));
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(DatabaseConfig.getPrincipalUser(principal));
        config.setPassword(DatabaseConfig.getPrincipalPassword(principal));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(15_000);
        config.setInitializationFailTimeout(-1);
        config.setAutoCommit(false);
        return new HikariDataSource(config);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        cleanup =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon(true).name("event-image-cleanup").factory());
        cleanup.scheduleWithFixedDelay(
                () -> {
                    Object value = event.getServletContext().getAttribute(SERVICE_ATTRIBUTE);
                    if (value instanceof EventService service)
                        try {
                            service.cleanup(10);
                        } catch (RuntimeException e) {
                            LOG.warning("Event image cleanup will retry");
                        }
                },
                60,
                60,
                TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (cleanup != null) cleanup.shutdownNow();
        Object value = event.getServletContext().getAttribute(POOLS_ATTRIBUTE);
        if (value instanceof List<?> pools)
            for (Object pool : pools) if (pool instanceof HikariDataSource source) source.close();
        event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
        event.getServletContext().removeAttribute(POOLS_ATTRIBUTE);
    }
}
