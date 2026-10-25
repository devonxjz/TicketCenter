package vn.ticketscenter.dashboard.service;

import java.util.Objects;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.redis.RedisKeyValueBackend;

/** Optional shared admin projection. Current DB authority is required before get(). */
public final class DashboardCache {
    private static final String KEY = "tc:admin:overview:v1";

    private final RedisKeyValueBackend backend;

    public DashboardCache(RedisKeyValueBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static DashboardCache none() {
        return new DashboardCache(new RedisKeyValueBackend() {});
    }

    public static DashboardCache configured() {
        return AppConfig.getBoolean("finance.redis.enabled", true) ? redis() : none();
    }

    public static DashboardCache redis() {
        return new DashboardCache(RedisKeyValueBackend.redis());
    }

    public String get() {
        try {
            return backend.get(KEY);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void put(String json) {
        if (json == null || json.length() > 1_000_000) return;
        try {
            backend.setex(KEY, 60, json);
        } catch (RuntimeException ignored) {
        }
    }

    public void invalidate() {
        try {
            backend.delete(KEY);
        } catch (RuntimeException ignored) {
        }
    }

    /** Root calls only after a committed finance write; never during an open write transaction. */
    public static void invalidateAdminAfterCommit() {
        configured().invalidate();
    }
}
