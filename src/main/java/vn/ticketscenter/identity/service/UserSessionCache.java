package vn.ticketscenter.identity.service;

import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.model.UserStatus;

import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis cache-aside entry {@code auth:user:{id}} holding only the account status and auth version, never a
 * secret. Every operation degrades to a no-op when Redis is absent or failing, so callers fall back to SQL.
 */
public class UserSessionCache {

    private static final Logger LOGGER = Logger.getLogger(UserSessionCache.class.getName());
    private static final String KEY_PREFIX = "auth:user:";
    private static final int TTL_SECONDS = 180;

    /** The status name and auth version recorded for a user. */
    public record Entry(String status, int authVersion) {
        public boolean matches(int expectedAuthVersion) {
            return UserStatus.ACTIVE.name().equalsIgnoreCase(status) && authVersion == expectedAuthVersion;
        }
    }

    private final RedisClientProvider redis;

    public UserSessionCache(RedisClientProvider redis) {
        this.redis = redis;
    }

    /** A cache that never stores anything. */
    public static UserSessionCache disabled() {
        return new UserSessionCache(null);
    }

    public Optional<Entry> get(UUID userId) {
        if (redis == null) {
            return Optional.empty();
        }
        try (Jedis jedis = redis.getResource()) {
            String cached = jedis.get(key(userId));
            if (cached == null) {
                return Optional.empty();
            }
            String[] parts = cached.split(":");
            if (parts.length != 2) {
                return Optional.empty();
            }
            return Optional.of(new Entry(parts[0], Integer.parseInt(parts[1])));
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Không thể đọc Redis cache cho user session, fallback DB: " + failure.getMessage());
            return Optional.empty();
        }
    }

    public void put(UUID userId, UserStatus status, int authVersion) {
        if (redis == null) {
            return;
        }
        try (Jedis jedis = redis.getResource()) {
            jedis.setex(key(userId), TTL_SECONDS, status.name() + ":" + authVersion);
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Không thể ghi Redis cache cho user session: " + failure.getMessage());
        }
    }

    public void evict(UUID userId) {
        if (userId == null || redis == null) {
            return;
        }
        try (Jedis jedis = redis.getResource()) {
            jedis.del(key(userId));
        } catch (Exception failure) {
            LOGGER.log(Level.WARNING, "Lỗi khi xóa cache user: " + failure.getMessage());
        }
    }

    private static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }
}
