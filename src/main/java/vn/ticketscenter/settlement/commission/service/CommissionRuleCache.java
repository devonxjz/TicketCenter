package vn.ticketscenter.settlement.commission.service;

import java.util.*;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisKeyValueBackend;

/** Optional list acceleration; callers must recheck current authority before read. */
public final class CommissionRuleCache {
    private final RedisKeyValueBackend backend;

    public CommissionRuleCache(RedisKeyValueBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static CommissionRuleCache none() {
        return new CommissionRuleCache(new RedisKeyValueBackend() {});
    }

    public static CommissionRuleCache redis() {
        return new CommissionRuleCache(RedisKeyValueBackend.redis());
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
                backend.setex(key(org), 300, json);
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
