package vn.ticketscenter.config.redis;

import java.util.function.Supplier;
import redis.clients.jedis.Jedis;

public interface RedisKeyValueBackend {
    /** Larger payloads are not cached. */
    int MAX_CACHED_LENGTH = 1_000_000;

    default String get(String key) {
        return null;
    }

    default void setex(String key, int seconds, String value) {}

    default void delete(String key) {}

    default void increment(String key) {}

    /** The cached value, or null on a miss or when Redis fails; SQL stays authoritative. */
    default String getQuietly(String key) {
        if (key == null) return null;
        try {
            return get(key);
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** Caches {@code value} up to {@link #MAX_CACHED_LENGTH}; Redis failures are ignored. */
    default void putQuietly(String key, int seconds, String value) {
        if (key == null || value == null || value.length() > MAX_CACHED_LENGTH) return;
        try {
            setex(key, seconds, value);
        } catch (RuntimeException unavailable) {
        }
    }

    default void deleteQuietly(String key) {
        try {
            delete(key);
        } catch (RuntimeException unavailable) {
        }
    }

    /** Cached value of {@code key}, else the authoritative value cached for {@code seconds}. */
    default String readThrough(String key, int seconds, Supplier<String> authoritative) {
        String hit = getQuietly(key);
        if (hit != null) return hit;
        String value = authoritative.get();
        putQuietly(key, seconds, value);
        return value;
    }

    static RedisKeyValueBackend redis() {
        return redis(() -> RedisClientProvider.getInstance().getResource());
    }

    static RedisKeyValueBackend redis(Supplier<Jedis> connections) {
        return new RedisKeyValueBackend() {
            @Override
            public String get(String key) {
                try (var redis = connections.get()) {
                    return redis.get(key);
                }
            }

            @Override
            public void setex(String key, int seconds, String value) {
                try (var redis = connections.get()) {
                    redis.setex(key, seconds, value);
                }
            }

            @Override
            public void delete(String key) {
                try (var redis = connections.get()) {
                    redis.del(key);
                }
            }

            @Override
            public void increment(String key) {
                try (var redis = connections.get()) {
                    redis.incr(key);
                }
            }
        };
    }
}
