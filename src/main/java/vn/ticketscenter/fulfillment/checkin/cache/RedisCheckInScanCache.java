package vn.ticketscenter.fulfillment.checkin.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.redis.RedisScan;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class RedisCheckInScanCache implements CheckInScanCache {
    private final Supplier<Jedis> connections;

    public RedisCheckInScanCache() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public RedisCheckInScanCache(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    @Override
    public Lease lock(UUID ticketId) {
        if (ticketId == null) return () -> {};
        String key = "tc:lock:ticket-checkin:" + ticketId;
        String token = UUID.randomUUID().toString();
        try (Jedis redis = connections.get()) {
            if (!"OK".equals(redis.set(key, token, SetParams.setParams().nx().ex(5))))
                return () -> {};
        } catch (RuntimeException ignored) {
            return () -> {};
        }

        return () -> {
            try (Jedis redis = connections.get()) {
                redis.eval(
                        "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
                        List.of(key),
                        List.of(token));
            } catch (RuntimeException ignored) {
            }
        };
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
