package vn.ticketscenter.ticketing.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisHoldIdempotencyIT {
    private static final String HASH = "a".repeat(64);
    private static final String DIFFERENT_HASH = "b".repeat(64);
    private static final RedisHoldIdempotency.CompletedResponse RESPONSE = new RedisHoldIdempotency.CompletedResponse(
            201, "application/json; charset=UTF-8", "{\"data\":{\"label\":\"Vé | giữ\"}}\n");
    private final UUID actor = UUID.randomUUID();
    private final List<String> createdKeys = new ArrayList<>();
    private JedisPool pool;
    private RedisHoldIdempotency store;

    @BeforeEach
    void connectToExplicitLoopbackFixture() throws Exception {
        String host = System.getProperty("liem.hold.redisTestHost");
        String port = System.getProperty("liem.hold.redisTestPort");
        assertNotNull(host, "explicit liem.hold.redisTestHost is required");
        assertNotNull(port, "explicit liem.hold.redisTestPort is required");
        assertFalse(host.isBlank(), "Redis fixture host must not be blank");
        InetAddress[] addresses = InetAddress.getAllByName(host);
        for (InetAddress address : addresses) {
            assertTrue(address.isLoopbackAddress(), "Redis fixture must use loopback only");
        }
        int fixturePort = Integer.parseInt(port);
        assertTrue(fixturePort >= 1 && fixturePort <= 65535, "Redis fixture port must be 1..65535");
        var config = new JedisPoolConfig();
        config.setMaxTotal(8);
        config.setMaxWait(Duration.ofSeconds(5));
        pool = new JedisPool(config, addresses[0].getHostAddress(), fixturePort, 2000);
        try (Jedis jedis = pool.getResource()) {
            assertEquals("PONG", jedis.ping());
        }
        store = new RedisHoldIdempotency(pool);
    }

    @AfterEach
    void removeOnlyExactFixtureKeys() {
        if (pool == null) {
            return;
        }
        try (var fixturePool = pool) {
            if (!createdKeys.isEmpty()) {
                try (Jedis jedis = fixturePool.getResource()) {
                    jedis.del(createdKeys.toArray(String[]::new));
                }
            }
        }
    }

    @Test
    void concurrentSameKeyHasExactlyOneClaim() throws Exception {
        String key = fixtureKey(actor);
        var ready = new CountDownLatch(8);
        var start = new CountDownLatch(1);
        var results = new ArrayList<RedisHoldIdempotency.Result>();
        try (var workers = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<java.util.concurrent.Future<RedisHoldIdempotency.Result>>();
            for (int index = 0; index < 8; index++) {
                futures.add(workers.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    return store.acquire(actor, key, HASH);
                }));
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            for (var future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
        }
        assertEquals(1L, results.stream().filter(RedisHoldIdempotency.Acquired.class::isInstance).count());
        assertEquals(7L, results.stream().filter(RedisHoldIdempotency.InProgress.class::isInstance).count());
    }

    @Test
    void completionReplaysExactResponseAndRejectsPayloadConflict() {
        String key = fixtureKey(actor);
        var claim = acquired(actor, key, HASH);
        assertInstanceOf(RedisHoldIdempotency.Conflict.class, store.acquire(actor, key, DIFFERENT_HASH));
        assertTrue(store.complete(claim, RESPONSE, Duration.ofMinutes(20)));
        var replay = assertInstanceOf(RedisHoldIdempotency.Replay.class, store.acquire(actor, key, HASH));
        assertEquals(RESPONSE, replay.response());
        assertEquals(201, replay.response().status());
        assertEquals("application/json; charset=UTF-8", replay.response().contentType());
        assertEquals("{\"data\":{\"label\":\"Vé | giữ\"}}\n", replay.response().body());
        assertInstanceOf(RedisHoldIdempotency.Conflict.class, store.acquire(actor, key, DIFFERENT_HASH));
    }

    @Test
    void sameClientKeyIsIsolatedBetweenActors() {
        String key = fixtureKey(actor);
        UUID otherActor = UUID.randomUUID();
        createdKeys.add(RedisHoldIdempotency.redisKey(otherActor, key));
        var first = acquired(actor, key, HASH);
        var second = acquired(otherActor, key, DIFFERENT_HASH);
        assertTrue(store.complete(first, RESPONSE, Duration.ofSeconds(30)));
        assertInstanceOf(RedisHoldIdempotency.InProgress.class, store.acquire(otherActor, key, DIFFERENT_HASH));
        assertTrue(store.complete(second, new RedisHoldIdempotency.CompletedResponse(204, "application/json", ""),
                Duration.ofSeconds(30)));
        assertEquals("", assertInstanceOf(RedisHoldIdempotency.Replay.class,
                store.acquire(otherActor, key, DIFFERENT_HASH)).response().body());
    }

    @Test
    void pendingClaimHasNoExpiryAndCannotBeReacquired() {
        String key = fixtureKey(actor);
        acquired(actor, key, HASH);
        try (Jedis jedis = pool.getResource()) {
            assertEquals(-1L, jedis.pttl(RedisHoldIdempotency.redisKey(actor, key)));
        }
        for (int attempt = 0; attempt < 3; attempt++) {
            assertInstanceOf(RedisHoldIdempotency.InProgress.class, store.acquire(actor, key, HASH));
        }
        try (Jedis jedis = pool.getResource()) {
            assertEquals(-1L, jedis.pttl(RedisHoldIdempotency.redisKey(actor, key)));
        }
    }

    @Test
    void completedEntryHasBoundedRetentionAndStaleCompletionCannotReplaceIt() {
        String key = fixtureKey(actor);
        var claim = acquired(actor, key, HASH);
        assertTrue(store.complete(claim, RESPONSE, Duration.ofSeconds(30)));
        long before;
        try (Jedis jedis = pool.getResource()) {
            before = jedis.pttl(RedisHoldIdempotency.redisKey(actor, key));
        }
        assertTrue(before > 0 && before <= 30_000);
        assertFalse(store.complete(claim, new RedisHoldIdempotency.CompletedResponse(409, "application/json", "changed"),
                Duration.ofDays(30)));
        assertEquals(RESPONSE, assertInstanceOf(RedisHoldIdempotency.Replay.class, store.acquire(actor, key, HASH)).response());
        try (Jedis jedis = pool.getResource()) {
            long after = jedis.pttl(RedisHoldIdempotency.redisKey(actor, key));
            assertTrue(after > 0 && after <= before);
        }
    }

    @Test
    void oldClaimCannotCompleteNewClaimAfterFixtureSimulatesRedisDataLoss() {
        String key = fixtureKey(actor);
        var oldClaim = acquired(actor, key, HASH);
        try (Jedis jedis = pool.getResource()) {
            jedis.del(RedisHoldIdempotency.redisKey(actor, key));
        }
        var newClaim = acquired(actor, key, HASH);
        assertFalse(store.complete(oldClaim, RESPONSE, Duration.ofSeconds(30)));
        assertInstanceOf(RedisHoldIdempotency.InProgress.class, store.acquire(actor, key, HASH));
        assertTrue(store.complete(newClaim, RESPONSE, Duration.ofSeconds(30)));
    }

    @Test
    void corruptStoredDataIsUnavailableForAcquireAndCompletion() {
        String key = fixtureKey(actor);
        var claim = acquired(actor, key, HASH);
        try (Jedis jedis = pool.getResource()) {
            jedis.set(RedisHoldIdempotency.redisKey(actor, key), "corrupt");
        }
        assertThrows(HoldIdempotencyUnavailableException.class, () -> store.acquire(actor, key, HASH));
        assertThrows(HoldIdempotencyUnavailableException.class, () -> store.complete(claim, RESPONSE, Duration.ofSeconds(30)));
    }

    @Test
    void closedRedisPoolBecomesUnavailableForAcquireAndCompletion() {
        String key = fixtureKey(actor);
        var claim = acquired(actor, key, HASH);
        try (var closedPool = new JedisPool("127.0.0.1", Integer.parseInt(System.getProperty("liem.hold.redisTestPort")))) {
            var closedStore = new RedisHoldIdempotency(closedPool);
            closedPool.close();
            var failure = assertThrows(HoldIdempotencyUnavailableException.class, () -> closedStore.acquire(actor, key, HASH));
            assertEquals("Hold idempotency is unavailable", failure.getMessage());
            assertNull(failure.getCause());
            assertThrows(HoldIdempotencyUnavailableException.class, () -> closedStore.complete(claim, RESPONSE, Duration.ofSeconds(30)));
        }
    }

    private String fixtureKey(UUID actorId) {
        String key = "it-" + UUID.randomUUID();
        createdKeys.add(RedisHoldIdempotency.redisKey(actorId, key));
        return key;
    }

    private RedisHoldIdempotency.Claim acquired(UUID actorId, String key, String hash) {
        return assertInstanceOf(RedisHoldIdempotency.Acquired.class, store.acquire(actorId, key, hash)).claim();
    }
}
