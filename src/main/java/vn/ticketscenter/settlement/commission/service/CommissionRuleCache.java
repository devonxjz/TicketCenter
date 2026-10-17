package vn.ticketscenter.settlement.commission.service;

import java.util.*;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisClientProvider;

/** Optional list acceleration; callers must recheck current authority before read. */
public final class CommissionRuleCache {
    public interface Backend {
        String get(String key);

        void put(String key, String value, int seconds);

        void delete(String key);
    }

    private final Backend backend;

    public CommissionRuleCache(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static CommissionRuleCache none() {
        return new CommissionRuleCache(
                new Backend() {
                    public String get(String key) {
                        return null;
                    }

                    public void put(String key, String value, int seconds) {}

                    public void delete(String key) {}
                });
    }

    public static CommissionRuleCache redis() {
        return new CommissionRuleCache(
                new Backend() {
                    public String get(String key) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            return redis.get(key);
                        }
                    }

                    public void put(String key, String value, int seconds) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            redis.setex(key, seconds, value);
                        }
                    }

                    public void delete(String key) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            redis.del(key);
                        }
                    }
                });
    }

    public String read(UUID org, Supplier<String> current) {
        try {
            var cached = backend.get(key(org));
            if (cached != null) return cached;
        } catch (RuntimeException unavailable) {
            /* Database fallback. */
        }
        String json = current.get();
        if (json.length() <= 1_000_000)
            try {
                backend.put(key(org), json, 300);
            } catch (RuntimeException unavailable) {
                /* Database is authoritative. */
            }
        return json;
    }

    public void invalidate(UUID org) {
        try {
            backend.delete(key(org));
        } catch (RuntimeException unavailable) {
            /* Bounded TTL; no financial authority in Redis. */
        }
    }

    private static String key(UUID org) {
        return "tc:commission:rules:v1:" + Objects.requireNonNull(org);
    }
}
