package vn.ticketscenter.report.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import vn.ticketscenter.config.redis.RedisKeyValueBackend;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.report.dto.ReportFilter;

/** Optional 60-second acceleration. Current DB authorization always precedes use. */
public final class ReportCache {
    private final RedisKeyValueBackend backend;

    public ReportCache(RedisKeyValueBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static ReportCache none() {
        return new ReportCache(new RedisKeyValueBackend() {});
    }

    public static ReportCache redis() {
        return new ReportCache(RedisKeyValueBackend.redis());
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
            backend.setex(key, 60, json);
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
