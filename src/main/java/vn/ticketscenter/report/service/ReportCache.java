package vn.ticketscenter.report.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;

/** Optional 60-second acceleration. Current DB authorization always precedes use. */
public final class ReportCache {
    public interface Backend {
        String get(String key);

        void set(String key, String value, int ttl);

        void increment(String key);
    }

    private final Backend backend;

    public ReportCache(Backend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static ReportCache none() {
        return new ReportCache(
                new Backend() {
                    public String get(String k) {
                        return null;
                    }

                    public void set(String k, String v, int t) {}

                    public void increment(String k) {}
                });
    }

    public static ReportCache redis() {
        return new ReportCache(
                new Backend() {
                    public String get(String key) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            return redis.get(key);
                        }
                    }

                    public void set(String key, String value, int ttl) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            redis.setex(key, ttl, value);
                        }
                    }

                    public void increment(String key) {
                        try (var redis = RedisClientProvider.getInstance().getResource()) {
                            redis.incr(key);
                        }
                    }
                });
    }

    public String key(UserPrincipal actor, ReportFilter filter) {
        try {
            String scope =
                    filter.organizationId() == null ? "global" : "org:" + filter.organizationId();
            String generation = backend.get("tc:finance:report:g:" + scope);
            // Initialize the counter; cached payloads remain bounded by their 60-second TTL.
            if (generation == null) {
                backend.increment("tc:finance:report:g:" + scope);
                generation = backend.get("tc:finance:report:g:" + scope);
            }
            if (generation == null) return null;
            String identity =
                    actor.actorId() + "|" + actor.authVersion() + "|" + filter.cacheIdentity();
            String hash =
                    HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(identity.getBytes(StandardCharsets.UTF_8)));
            return "tc:finance:report:v1:" + scope + ":" + generation + ":" + hash;
        } catch (Exception e) {
            return null;
        }
    }

    public String get(String key) {
        if (key == null) return null;
        try {
            return backend.get(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void put(String key, String json) {
        if (key == null || json.length() > 1_000_000) return;
        try {
            backend.set(key, json, 60);
        } catch (RuntimeException ignored) {
        }
    }

    /** Call only after committed financial writes; global and tenant pages share generations. */
    public static void invalidateAfterCommit(UUID organizationId) {
        var cache = redis();
        try {
            cache.backend.increment("tc:finance:report:g:global");
            if (organizationId != null)
                cache.backend.increment("tc:finance:report:g:org:" + organizationId);
        } catch (RuntimeException ignored) {
        }
    }
}
