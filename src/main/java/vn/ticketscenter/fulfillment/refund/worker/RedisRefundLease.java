package vn.ticketscenter.fulfillment.refund.worker;

import java.util.*;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.redis.RedisOwnedLease;

/** Redis outage falls back to the SQL dispatch claim; owned leases use compare-and-delete. */
public final class RedisRefundLease implements RefundLease {
    private final Supplier<Jedis> connections;

    public RedisRefundLease() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public RedisRefundLease(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    public static String key(UUID id) {
        return "tc:lease:refund:" + id;
    }

    @Override
    public Optional<Handle> acquire(UUID id) {
        try {
            return RedisOwnedLease.acquire(connections, key(id), SetParams.setParams().ex(120))
                    .map(release -> release::run);
        } catch (RuntimeException unavailable) {
            return Optional.of(() -> {});
        }
    }
}
