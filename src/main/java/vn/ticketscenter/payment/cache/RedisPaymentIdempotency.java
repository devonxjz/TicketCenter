package vn.ticketscenter.payment.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

public final class RedisPaymentIdempotency {
    public static final Duration DEFAULT_RETENTION = Duration.ofMinutes(10);
    public static final Duration LOCK_TIMEOUT = Duration.ofSeconds(5);

    private static final String COMPLETE_LUA = """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('PSETEX', KEYS[1], ARGV[3], ARGV[2])
            return 1
            """;

    private final Supplier<Jedis> connections;
    private final ConcurrentMap<String, MemoryRecord> memoryStore;
    private final ConcurrentMap<UUID, LockRecord> memoryLocks;

    private record MemoryRecord(String value, long expiresAtMillis) {
        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

    private record LockRecord(String lockVal, long expiresAtMillis) {
        boolean isExpired(long now) {
            return now >= expiresAtMillis;
        }
    }

    public RedisPaymentIdempotency(Supplier<Jedis> connections) {
        this.connections = Objects.requireNonNull(connections, "connections supplier is required");
        this.memoryStore = null;
        this.memoryLocks = null;
    }

    private RedisPaymentIdempotency() {
        this.connections = null;
        this.memoryStore = new ConcurrentHashMap<>();
        this.memoryLocks = new ConcurrentHashMap<>();
    }

    public static RedisPaymentIdempotency inMemory() {
        return new RedisPaymentIdempotency();
    }

    public sealed interface Result permits Acquired, InProgress, Replay, Conflict { }
    public record Acquired(Claim claim) implements Result { }
    public record InProgress() implements Result { }
    public record Replay(String cachedBody) implements Result { }
    public record Conflict() implements Result { }

    public static final class Claim {
        private final String redisKey;
        private final String requestHash;
        private final String pendingMarker;
        private final Supplier<Jedis> connections;
        private final ConcurrentMap<String, MemoryRecord> memoryStore;

        private Claim(String redisKey, String requestHash, String pendingMarker,
                      Supplier<Jedis> connections, ConcurrentMap<String, MemoryRecord> memoryStore) {
            this.redisKey = redisKey;
            this.requestHash = requestHash;
            this.pendingMarker = pendingMarker;
            this.connections = connections;
            this.memoryStore = memoryStore;
        }

        public boolean complete(String responseBody, Duration retention) {
            Objects.requireNonNull(responseBody, "responseBody is required");
            long ttlMs = Math.max(1000, (retention != null ? retention : DEFAULT_RETENTION).toMillis());
            String storedValue = "COMPLETED|" + requestHash + "|" + responseBody;
            if (connections == null) {
                long now = System.currentTimeMillis();
                boolean[] completed = new boolean[1];
                memoryStore.compute(redisKey, (k, existing) -> {
                    if (existing != null && !existing.isExpired(now) && pendingMarker.equals(existing.value())) {
                        completed[0] = true;
                        return new MemoryRecord(storedValue, now + ttlMs);
                    }
                    return existing;
                });
                return completed[0];
            }
            try (Jedis client = connections.get()) {
                Object result = client.eval(COMPLETE_LUA, 1, redisKey, pendingMarker, storedValue, String.valueOf(ttlMs));
                return Long.valueOf(1).equals(result);
            }
        }

        public void cancel() {
            if (connections == null) {
                memoryStore.computeIfPresent(redisKey, (k, existing) -> {
                    if (pendingMarker.equals(existing.value())) {
                        return null;
                    }
                    return existing;
                });
                return;
            }
            try (Jedis client = connections.get()) {
                String current = client.get(redisKey);
                if (pendingMarker.equals(current)) {
                    client.del(redisKey);
                }
            }
        }
    }

    public Result checkOrClaim(UUID actorId, UUID idempotencyKey, String requestHash, Duration ttl) {
        Objects.requireNonNull(actorId, "actorId is required");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey is required");
        Objects.requireNonNull(requestHash, "requestHash is required");

        String redisKey = "tc:idem:payos:init:" + actorId + ":" + idempotencyKey;
        String pendingMarker = "PENDING|" + requestHash + "|" + UUID.randomUUID();
        long ttlMs = Math.max(1000, (ttl != null ? ttl : DEFAULT_RETENTION).toMillis());

        if (connections == null) {
            long now = System.currentTimeMillis();
            Result[] outcome = new Result[1];
            memoryStore.compute(redisKey, (k, existing) -> {
                if (existing == null || existing.isExpired(now)) {
                    outcome[0] = new Acquired(new Claim(redisKey, requestHash, pendingMarker, null, memoryStore));
                    return new MemoryRecord(pendingMarker, now + ttlMs);
                }
                String[] parts = existing.value().split("\\|", 3);
                if (parts.length < 2) {
                    outcome[0] = new Conflict();
                    return existing;
                }
                String state = parts[0];
                String savedHash = parts[1];
                if (!requestHash.equals(savedHash)) {
                    outcome[0] = new Conflict();
                    return existing;
                }
                if ("COMPLETED".equals(state) && parts.length == 3) {
                    outcome[0] = new Replay(parts[2]);
                    return existing;
                }
                outcome[0] = new InProgress();
                return existing;
            });
            return outcome[0];
        }

        try (Jedis client = connections.get()) {
            SetParams params = SetParams.setParams().nx().px(ttlMs);
            String setRes = client.set(redisKey, pendingMarker, params);
            if ("OK".equals(setRes)) {
                return new Acquired(new Claim(redisKey, requestHash, pendingMarker, connections, memoryStore));
            }

            String existing = client.get(redisKey);
            if (existing == null) {
                // Key expired between SET and GET, retry once
                setRes = client.set(redisKey, pendingMarker, params);
                if ("OK".equals(setRes)) {
                    return new Acquired(new Claim(redisKey, requestHash, pendingMarker, connections, memoryStore));
                }
                existing = client.get(redisKey);
            }

            if (existing == null) {
                return new InProgress();
            }

            String[] parts = existing.split("\\|", 3);
            if (parts.length < 2) {
                return new Conflict();
            }

            String state = parts[0];
            String savedHash = parts[1];

            if (!requestHash.equals(savedHash)) {
                return new Conflict();
            }

            if ("COMPLETED".equals(state) && parts.length == 3) {
                return new Replay(parts[2]);
            }

            return new InProgress();
        }
    }

    public AutoCloseable acquireOrderLock(UUID orderId) {
        Objects.requireNonNull(orderId, "orderId is required");
        if (connections == null) {
            long now = System.currentTimeMillis();
            String lockVal = UUID.randomUUID().toString();
            long lockExpires = now + LOCK_TIMEOUT.toMillis();
            LockRecord rec = memoryLocks.compute(orderId, (k, existing) -> {
                if (existing == null || existing.isExpired(now)) {
                    return new LockRecord(lockVal, lockExpires);
                }
                return existing;
            });
            if (!lockVal.equals(rec.lockVal())) {
                return null;
            }
            return () -> memoryLocks.computeIfPresent(orderId, (k, existing) -> {
                if (lockVal.equals(existing.lockVal())) {
                    return null;
                }
                return existing;
            });
        }

        String lockKey = "tc:lock:order-payment:" + orderId;
        String lockVal = UUID.randomUUID().toString();
        Jedis client = connections.get();
        try {
            SetParams params = SetParams.setParams().nx().px(LOCK_TIMEOUT.toMillis());
            String res = client.set(lockKey, lockVal, params);
            if (!"OK".equals(res)) {
                client.close();
                return null;
            }
            return () -> {
                try {
                    String current = client.get(lockKey);
                    if (lockVal.equals(current)) {
                        client.del(lockKey);
                    }
                } finally {
                    client.close();
                }
            };
        } catch (RuntimeException failure) {
            client.close();
            throw failure;
        }
    }

    public static final Duration WEBHOOK_RETENTION = Duration.ofDays(7);

    public String getCachedWebhook(String txnRef, String providerReference) {
        if (txnRef == null || txnRef.isBlank()) return null;
        String key = webhookKey(txnRef, providerReference);
        if (connections == null) {
            long now = System.currentTimeMillis();
            MemoryRecord rec = memoryStore.get(key);
            if (rec != null && !rec.isExpired(now)) {
                return rec.value();
            }
            return null;
        }
        try (Jedis client = connections.get()) {
            return client.get(key);
        }
    }

    public void cacheWebhook(String txnRef, String providerReference, String responseJson) {
        if (txnRef == null || txnRef.isBlank() || responseJson == null) return;
        String key = webhookKey(txnRef, providerReference);
        if (connections == null) {
            long now = System.currentTimeMillis();
            memoryStore.put(key, new MemoryRecord(responseJson, now + WEBHOOK_RETENTION.toMillis()));
            return;
        }
        try (Jedis client = connections.get()) {
            client.set(key, responseJson, SetParams.setParams().ex(WEBHOOK_RETENTION.toSeconds()));
        }
    }

    private static String webhookKey(String txnRef, String providerReference) {
        if (providerReference != null && !providerReference.isBlank()) {
            return "tc:idem:payos:webhook:" + txnRef + ":" + providerReference;
        }
        return "tc:idem:payos:webhook:" + txnRef;
    }
}
