package vn.ticketscenter.ticketing.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static vn.ticketscenter.ticketing.cache.HoldIdempotencyAssertions.assertUnavailable;
import static org.junit.jupiter.api.Assertions.*;

class RedisHoldIdempotencyTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String HASH = "a".repeat(64);
    private static final RedisHoldIdempotency.CompletedResponse RESPONSE =
            new RedisHoldIdempotency.CompletedResponse(201, "application/json", "{\"data\":{}}");

    @Test
    void keyUsesActorScopeAndDoesNotTrim() {
        assertEquals("tc:idem:hold:00000000-0000-0000-0000-000000000001:Tab-A:1",
                RedisHoldIdempotency.redisKey(ACTOR, "Tab-A:1"));
        assertNotEquals(RedisHoldIdempotency.redisKey(ACTOR, "key"),
                RedisHoldIdempotency.redisKey(UUID.randomUUID(), "key"));
        assertEquals("tc:idem:hold:00000000-0000-0000-0000-000000000001:" + "x".repeat(128),
                RedisHoldIdempotency.redisKey(ACTOR, "x".repeat(128)));
        for (String invalid : List.of("", " key", "key ", "ke y", "key\n", "é", "x".repeat(129))) {
            assertThrows(IllegalArgumentException.class, () -> RedisHoldIdempotency.redisKey(ACTOR, invalid));
        }
        assertThrows(IllegalArgumentException.class, () -> RedisHoldIdempotency.redisKey(ACTOR, null));
        assertThrows(IllegalArgumentException.class, () -> RedisHoldIdempotency.redisKey(null, "key"));
        assertThrows(IllegalArgumentException.class, () -> RedisHoldIdempotency.redisKey(new UUID(0, 0), "key"));
    }

    @Test
    void malformedHashFailsBeforeBorrowingRedis() {
        var store = new RedisHoldIdempotency(() -> { throw new AssertionError("must not borrow Redis"); });
        for (String hash : List.of("", "a".repeat(63), "A".repeat(64), "z".repeat(64))) {
            assertThrows(IllegalArgumentException.class, () -> store.acquire(ACTOR, "key", hash));
        }
        assertThrows(IllegalArgumentException.class, () -> store.acquire(ACTOR, "key", null));
    }

    @Test
    void retentionRequiresBoundedWholeMillisecondsBeforeBorrowingRedis() {
        var unavailable = new AtomicBoolean(false);
        var store = new RedisHoldIdempotency(() -> {
            if (unavailable.get()) {
                throw new AssertionError("must not borrow Redis");
            }
            return new Jedis() {
                @Override public String set(String key, String value, SetParams params) { return "OK"; }
                @Override public void close() { }
            };
        });
        var claim = assertInstanceOf(RedisHoldIdempotency.Acquired.class, store.acquire(ACTOR, "key", HASH)).claim();
        unavailable.set(true);
        for (Duration retention : List.of(Duration.ZERO, Duration.ofSeconds(-1), Duration.ofNanos(1),
                Duration.ofMillis(1).plusNanos(1), Duration.ofDays(31))) {
            assertThrows(IllegalArgumentException.class, () -> store.complete(claim, RESPONSE, retention));
        }
        assertThrows(IllegalArgumentException.class, () -> store.complete(claim, RESPONSE, null));
    }

    @Test
    void providerFailureBecomesOneSafeTypedError() {
        var store = new RedisHoldIdempotency(() -> { throw new IllegalStateException("provider-secret"); });
        var failure = assertUnavailable(() -> store.acquire(ACTOR, "key", HASH));
        assertEquals("Hold idempotency is unavailable", failure.getMessage());
        assertNull(failure.getCause());
    }

    @Test
    void missingAndCorruptExistingEntriesBecomeUnavailable() {
        assertUnavailableEntry(null);
        for (String value : List.of("garbage", "1|P|" + HASH + "|not-a-uuid", "1|X|" + HASH + "|x",
                "1|C|" + HASH + "|201|invalid!|", "1|C|" + HASH + "|201|YQ|_w",
                "1|C|" + HASH + "|0199|YQ|", "1|P|invalid|00000000-0000-0000-0000-000000000001")) {
            assertUnavailableEntry(value);
        }
    }

    @Test
    void incompleteResponsesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RedisHoldIdempotency.CompletedResponse(0, "application/json", ""));
        assertThrows(IllegalArgumentException.class, () -> new RedisHoldIdempotency.CompletedResponse(201, "", ""));
        assertThrows(IllegalArgumentException.class, () -> new RedisHoldIdempotency.CompletedResponse(201, "application/json", null));
        assertThrows(IllegalArgumentException.class, () -> new RedisHoldIdempotency.CompletedResponse(201, "application/json", "\uD800"));
        for (String contentType : List.of("application/json\r\nX-Test: injected", "application/json\t", "application/json\u007F")) {
            assertThrows(IllegalArgumentException.class, () -> new RedisHoldIdempotency.CompletedResponse(201, contentType, ""));
        }
        var valid = new RedisHoldIdempotency.CompletedResponse(204, "application/json; charset=UTF-8", "");
        assertEquals(204, valid.status());
        assertEquals("application/json; charset=UTF-8", valid.contentType());
        assertEquals("", valid.body());
    }

    private static void assertUnavailableEntry(String value) {
        var store = new RedisHoldIdempotency(() -> new Jedis() {
            @Override public String set(String key, String pending, SetParams params) { return null; }
            @Override public String get(String key) { return value; }
            @Override public void close() { }
        });
        var failure = assertUnavailable(() -> store.acquire(ACTOR, "key", HASH));
        assertEquals("Hold idempotency is unavailable", failure.getMessage());
    }
}
