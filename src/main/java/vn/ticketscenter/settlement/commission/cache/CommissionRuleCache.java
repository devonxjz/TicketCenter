package vn.ticketscenter.settlement.commission.cache;

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
        return backend.readThrough(key(org), 300, current);
    }

    /** Bounded TTL; no financial authority in Redis. */
    public void invalidate(UUID org) {
        backend.deleteQuietly(key(org));
    }

    private static String key(UUID org) {
        return "tc:commission:rules:v1:" + Objects.requireNonNull(org);
    }
}
