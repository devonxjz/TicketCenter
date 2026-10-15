package vn.ticketscenter.fulfillment.refund.worker;

import java.util.*;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;

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
        String key = key(id), token = UUID.randomUUID().toString();
        try (var r = connections.get()) {
            if (!"OK".equals(r.set(key, token, SetParams.setParams().nx().ex(120))))
                return Optional.empty();
        } catch (RuntimeException unavailable) {
            return Optional.of(() -> {});
        }
        return Optional.of(
                () -> {
                    try (var r = connections.get()) {
                        r.eval(
                                "if redis.call('get',KEYS[1]) == ARGV[1] then return"
                                    + " redis.call('del',KEYS[1]) else return 0 end",
                                List.of(key),
                                List.of(token));
                    } catch (RuntimeException ignored) {
                    }
                });
    }
}
