package vn.ticketscenter.config.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import vn.ticketscenter.config.AppConfig;

import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Quản lý kết nối tập trung tới Redis thông qua JedisPool (bounded pool).
 * Cấu hình lấy từ AppConfig (System properties -> Environment variables -> application.properties).
 */
public final class RedisClientProvider implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RedisClientProvider.class.getName());
    private static volatile RedisClientProvider instance;

    private final JedisPool jedisPool;

    private RedisClientProvider() {
        String host = AppConfig.get(AppConfig.Keys.REDIS_HOST, "localhost");
        int port = AppConfig.getInt(AppConfig.Keys.REDIS_PORT, 6379);
        String password = AppConfig.get(AppConfig.Keys.REDIS_PASSWORD, "");
        int timeout = AppConfig.getInt(AppConfig.Keys.REDIS_TIMEOUT, 2000);

        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(32);
        poolConfig.setMaxIdle(16);
        poolConfig.setMinIdle(4);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
        poolConfig.setBlockWhenExhausted(true);
        poolConfig.setMaxWait(Duration.ofMillis(timeout));

        if (password != null && !password.isBlank()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout);
        }

        LOGGER.info(String.format("Khởi tạo JedisPool tới Redis tại %s:%d (timeout=%dms)", host, port, timeout));
    }

    public static RedisClientProvider getInstance() {
        if (instance == null) {
            synchronized (RedisClientProvider.class) {
                if (instance == null) {
                    instance = new RedisClientProvider();
                }
            }
        }
        return instance;
    }

    /**
     * Mượn 1 kết nối Jedis từ pool. Caller phải đóng kết nối (dùng try-with-resources).
     */
    public Jedis getResource() {
        if (jedisPool == null || jedisPool.isClosed()) {
            throw new IllegalStateException("Redis pool chưa được khởi tạo hoặc đã bị đóng");
        }
        return jedisPool.getResource();
    }

    /**
     * Kiểm tra nhanh trạng thái kết nối tới Redis (PING).
     */
    public boolean isAvailable() {
        try (Jedis jedis = getResource()) {
            return "PONG".equalsIgnoreCase(jedis.ping());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Không thể kết nối tới Redis: " + e.getMessage());
            return false;
        }
    }

    public JedisPool getPool() {
        return jedisPool;
    }

    @Override
    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
            LOGGER.info("Đã đóng JedisPool");
        }
    }
}
