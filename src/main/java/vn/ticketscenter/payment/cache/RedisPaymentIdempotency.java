package vn.ticketscenter.payment.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
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

    public RedisPaymentIdempotency(Supplier<Jedis> connections) {
        this.connections = Objects.requireNonNull(connections, "connections supplier is required");
    }

    public RedisPaymentIdempotency(JedisPool pool) {
        this(Objects.requireNonNull(pool)::getResource);
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

        private Claim(String redisKey, String requestHash, String pendingMarker, Supplier<Jedis> connections) {
            this.redisKey = redisKey;
            this.requestHash = requestHash;
            this.pendingMarker = pendingMarker;
            this.connections = connections;
        }

        public boolean complete(String responseBody, Duration retention) {
            Objects.requireNonNull(responseBody, "responseBody is required");
            long ttlMs = Math.max(1000, (retention != null ? retention : DEFAULT_RETENTION).toMillis());
            String storedValue = "COMPLETED|" + requestHash + "|" + responseBody;
            try (Jedis client = connections.get()) {
                Object result = client.eval(COMPLETE_LUA, 1, redisKey, pendingMarker, storedValue, String.valueOf(ttlMs));
                return Long.valueOf(1).equals(result);
            }
        }

        public void cancel() {
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

        String redisKey = "tc:idem:vnpay:init:" + actorId + ":" + idempotencyKey;
        String pendingMarker = "PENDING|" + requestHash + "|" + UUID.randomUUID();
        long ttlMs = Math.max(1000, (ttl != null ? ttl : DEFAULT_RETENTION).toMillis());

        try (Jedis client = connections.get()) {
            SetParams params = SetParams.setParams().nx().px(ttlMs);
            String setRes = client.set(redisKey, pendingMarker, params);
            if ("OK".equals(setRes)) {
                return new Acquired(new Claim(redisKey, requestHash, pendingMarker, connections));
            }

            String existing = client.get(redisKey);
            if (existing == null) {
                // Key expired between SET and GET, retry once
                setRes = client.set(redisKey, pendingMarker, params);
                if ("OK".equals(setRes)) {
                    return new Acquired(new Claim(redisKey, requestHash, pendingMarker, connections));
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
}
