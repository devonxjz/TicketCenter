package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.*;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.redis.RedisOwnedLease;
import vn.ticketscenter.config.redis.RedisScan;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;

/** Redis reduces contention and clears display caches; SQL owns all financial decisions. */
public final class RedisRefundRequestCache implements RefundRequestCache {
    public static final int IDEMPOTENCY_TTL = 86400;
    private final Supplier<Jedis> connections;

    public RedisRefundRequestCache() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public RedisRefundRequestCache(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    public static String lockKey(List<UUID> ids) {
        return "tc:lock:refund-tickets:"
                + ids.stream().map(UUID::toString).sorted().collect(Collectors.joining("_"));
    }

    public static String idemKey(UUID actor, UUID key) {
        return "tc:idem:refund-request:" + actor + ":" + key;
    }

    @Override
    public Lease lock(List<UUID> ids) {
        try {
            return RedisOwnedLease.acquire(
                            connections, lockKey(ids), SetParams.setParams().px(10000))
                    .<Lease>map(release -> release::run)
                    .orElse(() -> {});
        } catch (RuntimeException unavailable) {
            return () -> {};
        }
    }

    @Override
    public void committed(UUID actor, UUID key, List<UUID> tickets, RefundSummaryDto result) {
        try (var redis = connections.get()) {
            for (UUID ticket : tickets) {
                redis.del("tc:ticket:detail:v1:" + ticket);
                RedisScan.deleteMatching(redis, "tc:ticket:qr:v1:" + ticket + ":*");
            }
            RedisScan.deleteMatching(redis, "tc:user:tickets:v1:" + actor + ":*");
            redis.del("tc:admin:pending_refunds_count");
            redis.setex(idemKey(actor, key), IDEMPOTENCY_TTL, result.json());
        } catch (RuntimeException ignored) {
            /* The mutation has already committed; never report it as failed. */
        }
    }
}
