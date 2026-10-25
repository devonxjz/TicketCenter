package vn.ticketscenter.config.redis;

import java.util.function.Supplier;
import redis.clients.jedis.Jedis;

public interface RedisKeyValueBackend {
    default String get(String key) {
        return null;
    }

    default void setex(String key, int seconds, String value) {}

    default void delete(String key) {}

    default void increment(String key) {}

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
