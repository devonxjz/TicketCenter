package vn.ticketscenter.config.redis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

public final class RedisOwnedLease {
    private static final String RELEASE =
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else"
                    + " return 0 end";

    private RedisOwnedLease() {}

    /** Empty on contention; Redis failures propagate. The release swallows its own errors. */
    public static Optional<Runnable> acquire(
            Supplier<Jedis> connections, String key, SetParams expiry) {
        String token = UUID.randomUUID().toString();
        try (Jedis redis = connections.get()) {
            if (!"OK".equals(redis.set(key, token, expiry.nx()))) {
                return Optional.empty();
            }
        }
        return Optional.of(
                () -> {
                    try (Jedis redis = connections.get()) {
                        redis.eval(RELEASE, List.of(key), List.of(token));
                    } catch (RuntimeException ignored) {
                    }
                });
    }
}
