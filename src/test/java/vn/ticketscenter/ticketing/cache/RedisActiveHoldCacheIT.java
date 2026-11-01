package vn.ticketscenter.ticketing.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.ticketing.dto.ActiveHoldProjection;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Base64;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RedisActiveHoldCacheIT extends HoldRedisFixture {
    @Test
    void fullCanonicalMoneyAndLongExpiryAreBoundedInRedis() {
        var cache = cache(Clock.systemUTC());
        var projection = new ActiveHoldProjection(UUID.randomUUID(), actor, UUID.randomUUID(),
                Instant.now().plusSeconds(3600), List.of(new ActiveHoldProjection.Item(UUID.randomUUID(), null, 1,
                        new BigDecimal("9999999999999999999"))));
        assertTrue(cache.fill(cache.lookup(actor).fillToken().orElseThrow(), projection));
        assertEquals(projection, cache.lookup(actor).projection().orElseThrow());
        try (Jedis jedis = pool.getResource()) {
            long ttl = jedis.pttl(RedisActiveHoldCache.redisKey(actor));
            assertTrue(ttl > 0 && ttl <= 600_000);
        }
    }

    @Test
    void roundTripsExactProjectionWithActorKeyAndBoundedTtl() {
        var cache = cache(Clock.systemUTC());
        var projection = projection(actor, Instant.now().plusSeconds(30).with(java.time.temporal.ChronoField.NANO_OF_SECOND, 123456789));
        var lookup = cache.lookup(actor);
        assertTrue(lookup.projection().isEmpty());
        assertMarkerTtl(actor);
        assertTrue(cache.fill(lookup.fillToken().orElseThrow(), projection));
        assertEquals(projection, cache.lookup(actor).projection().orElseThrow());
        assertEquals("tc:user:active-hold:" + actor, RedisActiveHoldCache.redisKey(actor));
        try (Jedis jedis = pool.getResource()) {
            long ttl = jedis.pttl(RedisActiveHoldCache.redisKey(actor));
            long remaining = projection.expiresAt().toEpochMilli() - Instant.now().toEpochMilli();
            assertTrue(ttl > 0 && ttl <= remaining + 5);
            assertTrue(jedis.get(RedisActiveHoldCache.redisKey(actor)).startsWith("1|D|"));
        }
    }

    @Test
    void invalidationAfterCommitFencesPriorReadAndNewReadCanFill() {
        var cache = cache(Clock.systemUTC());
        var oldToken = cache.lookup(actor).fillToken().orElseThrow();
        assertTrue(cache.invalidateAfterCommit(actor));
        assertMarkerTtl(actor);
        assertFalse(cache.fill(oldToken, projection(actor, Instant.now().plusSeconds(30))));
        var fresh = cache.lookup(actor);
        assertTrue(fresh.projection().isEmpty());
        assertTrue(cache.fill(fresh.fillToken().orElseThrow(), projection(actor, Instant.now().plusSeconds(30))));
        assertTrue(cache.lookup(actor).projection().isPresent());
        assertTrue(cache.invalidateAfterCommit(actor));
        assertTrue(cache.lookup(actor).projection().isEmpty());
        assertMarkerTtl(actor);
    }

    @Test
    void evictionOrRedisDataLossPreventsOldTokenFromRepopulating() {
        var cache = cache(Clock.systemUTC());
        var oldToken = cache.lookup(actor).fillToken().orElseThrow();
        try (Jedis jedis = pool.getResource()) { jedis.del(RedisActiveHoldCache.redisKey(actor)); }
        assertFalse(cache.fill(oldToken, projection(actor, Instant.now().plusSeconds(30))));
        var freshToken = cache.lookup(actor).fillToken().orElseThrow();
        assertFalse(cache.fill(oldToken, projection(actor, Instant.now().plusSeconds(30))));
        assertTrue(cache.fill(freshToken, projection(actor, Instant.now().plusSeconds(30))));
    }

    @Test
    void markerExpiryPreventsOldTokenFromRepopulatingBeforeAndAfterFreshSeed() {
        var cache = cache(Clock.systemUTC());
        var oldToken = cache.lookup(actor).fillToken().orElseThrow();
        assertMarkerTtl(actor);
        String key = RedisActiveHoldCache.redisKey(actor);
        String expiredMarker;
        try (Jedis jedis = pool.getResource()) {
            expiredMarker = jedis.get(key);
            assertEquals(1L, jedis.pexpireAt(key, 1L));
            assertEquals(-2L, jedis.pttl(key));
            assertNull(jedis.get(key));
        }
        var projection = projection(actor, Instant.now().plusSeconds(30));
        assertFalse(cache.fill(oldToken, projection));
        var freshToken = cache.lookup(actor).fillToken().orElseThrow();
        assertMarkerTtl(actor);
        try (Jedis jedis = pool.getResource()) { assertNotEquals(expiredMarker, jedis.get(key)); }
        assertFalse(cache.fill(oldToken, projection));
        assertTrue(cache.fill(freshToken, projection));
        assertEquals(projection, cache.lookup(actor).projection().orElseThrow());
    }

    @Test
    void tokensAndCachedPayloadCannotCrossActors() {
        var cache = cache(Clock.systemUTC());
        UUID other = UUID.randomUUID();
        keys.add(RedisActiveHoldCache.redisKey(other));
        var token = cache.lookup(actor).fillToken().orElseThrow();
        assertFalse(cache.fill(token, projection(other, Instant.now().plusSeconds(30))));
        assertTrue(cache.fill(token, projection(actor, Instant.now().plusSeconds(30))));
        try (Jedis jedis = pool.getResource()) {
            jedis.set(RedisActiveHoldCache.redisKey(other), jedis.get(RedisActiveHoldCache.redisKey(actor)));
        }
        var wrongBuyer = cache.lookup(other);
        assertTrue(wrongBuyer.projection().isEmpty());
        assertTrue(wrongBuyer.fillToken().isPresent());
        assertMarkerTtl(other);
        assertEquals(actor, cache.lookup(actor).projection().orElseThrow().buyerId());
    }

    @Test
    void exactExpiryIsAMissAndExpiredOrSubmillisecondSnapshotsCannotFill() {
        Instant expires = Instant.now().plusSeconds(30);
        var cache = cache(Clock.systemUTC());
        assertTrue(cache.fill(cache.lookup(actor).fillToken().orElseThrow(), projection(actor, expires)));
        assertTrue(cache(Clock.fixed(expires.minusNanos(1), ZoneOffset.UTC)).lookup(actor).projection().isPresent());
        var expiredCache = cache(Clock.fixed(expires, ZoneOffset.UTC));
        var miss = expiredCache.lookup(actor);
        assertTrue(miss.projection().isEmpty());
        assertMarkerTtl(actor);
        assertFalse(expiredCache.fill(miss.fillToken().orElseThrow(), projection(actor, expires)));
        var tinyCache = cache(Clock.fixed(expires.minusNanos(999_999), ZoneOffset.UTC));
        assertFalse(tinyCache.fill(tinyCache.lookup(actor).fillToken().orElseThrow(), projection(actor, expires)));
        try (Jedis jedis = pool.getResource()) { assertTrue(jedis.get(RedisActiveHoldCache.redisKey(actor)).startsWith("1|M|")); }
    }

    @Test
    void corruptOrOversizedPayloadIsAMissAndDoesNotBlockFutureFill() {
        var cache = cache(Clock.systemUTC());
        for (String corrupt : List.of("corrupt", "1|D|???", "1|D|" + "a".repeat(2000), "1|M|00000000-0000-0000-0000-000000000000")) {
            try (Jedis jedis = pool.getResource()) { jedis.set(RedisActiveHoldCache.redisKey(actor), corrupt); }
            var lookup = cache.lookup(actor);
            assertTrue(lookup.projection().isEmpty());
            assertMarkerTtl(actor);
            assertTrue(cache.fill(lookup.fillToken().orElseThrow(), projection(actor, Instant.now().plusSeconds(30))));
            assertTrue(cache.lookup(actor).projection().isPresent());
        }
    }

    @Test
    void malformedPrivateBinaryPayloadsAreMisses() {
        var cache = cache(Clock.systemUTC());
        assertTrue(cache.fill(cache.lookup(actor).fillToken().orElseThrow(), projection(actor, Instant.now().plusSeconds(30))));
        byte[] original;
        try (Jedis jedis = pool.getResource()) {
            original = Base64.getUrlDecoder().decode(jedis.get(RedisActiveHoldCache.redisKey(actor)).substring(4));
        }
        byte[] badVersion = original.clone();
        badVersion[0] = 3;
        byte[] badNanos = original.clone();
        java.nio.ByteBuffer.wrap(badNanos).putInt(57, -1);
        byte[] badCount = original.clone();
        badCount[61] = 9;
        byte[] badSeatFlag = original.clone();
        badSeatFlag[78] = 2;
        for (byte[] invalid : List.of(badVersion, badNanos, badCount, badSeatFlag,
                Arrays.copyOf(original, original.length - 1), Arrays.copyOf(original, original.length + 1))) {
            try (Jedis jedis = pool.getResource()) {
                jedis.set(RedisActiveHoldCache.redisKey(actor), "1|D|" + Base64.getUrlEncoder().withoutPadding().encodeToString(invalid));
            }
            assertTrue(cache.lookup(actor).projection().isEmpty());
            assertMarkerTtl(actor);
        }
    }

    @Test
    void redisTimeRejectsExpiredFillEvenWhenLocalClockIsBehind() {
        Instant expires = Instant.now().minusSeconds(1);
        var behind = cache(Clock.fixed(expires.minusSeconds(30), ZoneOffset.UTC));
        assertFalse(behind.fill(behind.lookup(actor).fillToken().orElseThrow(), projection(actor, expires)));
        try (Jedis jedis = pool.getResource()) {
            assertTrue(jedis.get(RedisActiveHoldCache.redisKey(actor)).startsWith("1|M|"));
        }
        assertMarkerTtl(actor);
    }

    @Test
    void unavailableRedisAllowsDbFallbackAndCannotFillOrInvalidate() {
        var connected = cache(Clock.systemUTC());
        var token = connected.lookup(actor).fillToken().orElseThrow();
        var unavailable = new RedisActiveHoldCache(() -> { throw new IllegalStateException("private provider data"); }, Clock.systemUTC());
        var miss = unavailable.lookup(actor);
        assertTrue(miss.projection().isEmpty());
        assertTrue(miss.fillToken().isEmpty());
        assertFalse(unavailable.fill(token, projection(actor, Instant.now().plusSeconds(30))));
        assertFalse(unavailable.invalidateAfterCommit(actor));
        assertTrue(connected.lookup(actor).projection().isEmpty());
    }

    @Test
    void concurrentFillsWithSameTokenCannotBothPublishSnapshots() throws Exception {
        var cache = cache(Clock.systemUTC());
        var token = cache.lookup(actor).fillToken().orElseThrow();
        var first = projection(actor, Instant.now().plusSeconds(30));
        var second = projection(actor, Instant.now().plusSeconds(30));
        var start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return cache.fill(token, first); });
            var two = workers.submit(() -> { assertTrue(start.await(5, TimeUnit.SECONDS)); return cache.fill(token, second); });
            start.countDown();
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
        }
        assertTrue(List.of(first, second).contains(cache.lookup(actor).projection().orElseThrow()));
    }

    private RedisActiveHoldCache cache(Clock clock) {
        String key = RedisActiveHoldCache.redisKey(actor);
        if (!keys.contains(key)) keys.add(key);
        return new RedisActiveHoldCache(pool::getResource, clock);
    }

    private void assertMarkerTtl(UUID buyer) {
        try (Jedis jedis = pool.getResource()) {
            String key = RedisActiveHoldCache.redisKey(buyer);
            assertTrue(jedis.get(key).startsWith("1|M|"));
            long ttl = jedis.pttl(key);
            assertTrue(ttl > 0 && ttl <= 600_000, "marker TTL must be positive and at most ten minutes, got " + ttl);
        }
    }

    private static ActiveHoldProjection projection(UUID buyer, Instant expires) {
        return new ActiveHoldProjection(UUID.randomUUID(), buyer, UUID.randomUUID(), expires, List.of(
                new ActiveHoldProjection.Item(UUID.randomUUID(), UUID.randomUUID(), 1, new BigDecimal("9223372036854775807")),
                new ActiveHoldProjection.Item(UUID.randomUUID(), null, 2, new BigDecimal("250000"))));
    }
}
