package vn.ticketscenter.fulfillment.checkin.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.redis.RedisOwnedLease;
import vn.ticketscenter.config.redis.RedisScan;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class CheckInScanCacheImpl implements CheckInScanCache {
    private final Supplier<Jedis> connections;

    public CheckInScanCacheImpl() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public CheckInScanCacheImpl(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    @Override
    public Lease lock(UUID ticketId) {
        if (ticketId == null) return () -> {};
        try {
            return RedisOwnedLease.acquire(
                            connections,
                            "tc:lock:ticket-checkin:" + ticketId,
                            SetParams.setParams().ex(5))
                    .<Lease>map(release -> release::run)
                    .orElse(() -> {});
        } catch (RuntimeException ignored) {
            return () -> {};
        }
    }

    @Override
    public void completed(UUID actor, UUID key, CheckInScanResponseDto result) {
        try (Jedis redis = connections.get()) {
            redis.setex("tc:idem:checkin:" + actor + ":" + key, 3600, result.toJson());
            if (!result.success()) return;
            redis.del("tc:ticket:detail:v1:" + result.ticketId());
            for (String pattern :
                    List.of(
                            "tc:ticket:qr:v1:" + result.ticketId() + ":*",
                            "tc:checkin:events:v1:*:" + result.organizationId())) {
                RedisScan.deleteMatching(redis, pattern);
            }
        } catch (RuntimeException ignored) {
            // SQL preserves correctness and durable replay during Redis outages.
        }
    }
}
