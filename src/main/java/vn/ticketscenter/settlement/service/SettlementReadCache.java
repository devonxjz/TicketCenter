package vn.ticketscenter.settlement.service;

import java.util.*;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisKeyValueBackend;

/** Read acceleration only. Caller checks current ADMIN and event existence before every hit. */
public final class SettlementReadCache {
    public enum Kind {
        SUMMARY("summary", 60),
        BLOCKERS("blockers", 30);
        final String name;
        final int ttl;

        Kind(String name, int ttl) {
            this.name = name;
            this.ttl = ttl;
        }
    }

    private final RedisKeyValueBackend backend;

    public SettlementReadCache(RedisKeyValueBackend backend) {
        this.backend = Objects.requireNonNull(backend);
    }

    public static SettlementReadCache none() {
        return new SettlementReadCache(new RedisKeyValueBackend() {});
    }

    public static SettlementReadCache redis() {
        return new SettlementReadCache(RedisKeyValueBackend.redis());
    }

    public String read(UUID event, Kind kind, Supplier<String> authoritative) {
        String key = key(event, kind);
        try {
            String hit = backend.get(key);
            if (hit != null) return hit;
        } catch (RuntimeException unavailable) {
        }
        String json = authoritative.get();
        if (json.length() <= 1_000_000)
            try {
                backend.setex(key, kind.ttl, json);
            } catch (RuntimeException unavailable) {
            }
        return json;
    }

    public void invalidate(UUID event) {
        for (Kind kind : Kind.values())
            try {
                backend.delete(key(event, kind));
            } catch (RuntimeException unavailable) {
            }
    }

    private static String key(UUID event, Kind kind) {
        return "tc:settlement:"
                + Objects.requireNonNull(kind).name
                + ":v1:"
                + Objects.requireNonNull(event);
    }
}
