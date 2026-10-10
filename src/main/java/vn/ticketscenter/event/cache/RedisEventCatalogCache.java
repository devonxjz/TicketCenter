package vn.ticketscenter.event.cache;

import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.redis.RedisClientProvider;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

public final class RedisEventCatalogCache implements EventCatalogCache {
    private static final int MAX_CHARACTERS = 2_000_000;
    private static final Logger LOGGER = Logger.getLogger(RedisEventCatalogCache.class.getName());
    private final AtomicLong nextWarning = new AtomicLong();
    private final Supplier<Jedis> connections;
    public RedisEventCatalogCache() { this(() -> RedisClientProvider.getInstance().getResource()); }
    public RedisEventCatalogCache(Supplier<Jedis> connections) { this.connections = connections; }
    @Override public Optional<String> get(String key) {
        try (Jedis redis = connections.get()) {
            String json = redis.get(key);
            return json != null && json.length() <= MAX_CHARACTERS && CachedJsonValidator.isResponse(json)
                    ? Optional.of(json) : Optional.empty();
        } catch (RuntimeException ignored) { unavailable(); return Optional.empty(); }
    }
    @Override public void put(String key, String json, int ttlSeconds) {
        if (json.length() > MAX_CHARACTERS || !CachedJsonValidator.isResponse(json)) return;
        try (Jedis redis = connections.get()) { redis.setex(key, ttlSeconds, json); }
        catch (RuntimeException ignored) { unavailable(); }
    }
    private void unavailable() {
        long now = System.currentTimeMillis(), next = nextWarning.get();
        if (now >= next && nextWarning.compareAndSet(next, now + 60_000))
            LOGGER.warning("Event catalog Redis unavailable; falling back to SQL Server.");
    }
}
