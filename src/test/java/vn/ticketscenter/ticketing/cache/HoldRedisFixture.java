package vn.ticketscenter.ticketing.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

abstract class HoldRedisFixture {
    protected final UUID actor = UUID.randomUUID();
    protected final List<String> keys = new ArrayList<>();
    protected JedisPool pool;
    protected String host;
    protected int port;

    @BeforeEach
    void connectToExplicitLoopbackFixture() throws Exception {
        host = System.getProperty("liem.hold.redisTestHost");
        String configuredPort = System.getProperty("liem.hold.redisTestPort");
        assertNotNull(host, "explicit liem.hold.redisTestHost is required");
        assertNotNull(configuredPort, "explicit liem.hold.redisTestPort is required");
        assertFalse(host.isBlank());
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) assertTrue(address.isLoopbackAddress(), "Redis fixture must use loopback only");
        port = Integer.parseInt(configuredPort);
        assertTrue(port >= 1 && port <= 65535);
        host = addresses[0].getHostAddress();
        var config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxWait(Duration.ofSeconds(5));
        pool = new JedisPool(config, host, port, 2000);
        try (Jedis jedis = pool.getResource()) { assertEquals("PONG", jedis.ping()); }
    }

    @AfterEach
    void cleanOnlyExactFixtureKeys() {
        if (pool == null) return;
        try (var fixturePool = pool) {
            if (!keys.isEmpty()) {
                try (Jedis jedis = fixturePool.getResource()) { jedis.del(keys.toArray(String[]::new)); }
            }
        }
    }
}
