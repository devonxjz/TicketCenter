package vn.ticketscenter.settlement.cache;

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
        return backend.readThrough(key(event, kind), kind.ttl, authoritative);
    }

    public void invalidate(UUID event) {
        for (Kind kind : Kind.values()) {
            backend.deleteQuietly(key(event, kind));
        }
    }

    private static String key(UUID event, Kind kind) {
        return "tc:settlement:"
                + Objects.requireNonNull(kind).name
                + ":v1:"
                + Objects.requireNonNull(event);
    }
}
