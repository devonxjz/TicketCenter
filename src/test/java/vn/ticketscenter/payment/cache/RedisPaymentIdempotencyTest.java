package vn.ticketscenter.payment.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RedisPaymentIdempotencyTest {
    private static final UUID ACTOR = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID KEY = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORDER = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final String HASH = "a".repeat(64);
    private static final String DIFF_HASH = "b".repeat(64);

    private static class MockJedis extends Jedis {
        final Map<String, String> store = new HashMap<>();

        @Override
        public String set(String key, String value, SetParams params) {
            if (store.containsKey(key)) {
                return null;
            }
            store.put(key, value);
            return "OK";
        }

        @Override
        public String get(String key) {
            return store.get(key);
        }

        @Override
        public long del(String key) {
            return store.remove(key) != null ? 1 : 0;
        }

        @Override
        public Object eval(String script, int keyCount, String... params) {
            String key = params[0];
            String expectedVal = params[1];
            String newVal = params[2];
            String current = store.get(key);
            if (Objects.equals(current, expectedVal)) {
                store.put(key, newVal);
                return 1L;
            }
            return 0L;
        }

        @Override
        public void close() { }
    }

    @Test
    void acquiresKeyOnFirstRequestAndReplaysOnCompletion() {
        MockJedis jedis = new MockJedis();
        RedisPaymentIdempotency idempotency = new RedisPaymentIdempotency(() -> jedis);

        var first = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(first instanceof RedisPaymentIdempotency.Acquired);
        var claim = ((RedisPaymentIdempotency.Acquired) first).claim();

        // While pending, same request sees InProgress
        var pending = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(pending instanceof RedisPaymentIdempotency.InProgress);

        // Complete the claim
        String responseBody = "{\"redirectUrl\":\"https://pay\"}";
        assertTrue(claim.complete(responseBody, Duration.ofMinutes(10)));

        // Now same key with same hash returns Replay
        var replay = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(replay instanceof RedisPaymentIdempotency.Replay);
        assertEquals(responseBody, ((RedisPaymentIdempotency.Replay) replay).cachedBody());
    }

    @Test
    void detectsConflictWhenHashDiffers() {
        MockJedis jedis = new MockJedis();
        RedisPaymentIdempotency idempotency = new RedisPaymentIdempotency(() -> jedis);

        var first = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(first instanceof RedisPaymentIdempotency.Acquired);

        // Same actor and key but different hash returns Conflict
        var conflict = idempotency.checkOrClaim(ACTOR, KEY, DIFF_HASH, Duration.ofMinutes(10));
        assertTrue(conflict instanceof RedisPaymentIdempotency.Conflict);
    }

    @Test
    void cancellingClaimRemovesPendingKey() {
        MockJedis jedis = new MockJedis();
        RedisPaymentIdempotency idempotency = new RedisPaymentIdempotency(() -> jedis);

        var first = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(first instanceof RedisPaymentIdempotency.Acquired);
        var claim = ((RedisPaymentIdempotency.Acquired) first).claim();

        claim.cancel();
        // After cancel, key is available again
        var second = idempotency.checkOrClaim(ACTOR, KEY, HASH, Duration.ofMinutes(10));
        assertTrue(second instanceof RedisPaymentIdempotency.Acquired);
    }

    @Test
    void orderLockPreventsConcurrentExecution() throws Exception {
        MockJedis jedis = new MockJedis();
        RedisPaymentIdempotency idempotency = new RedisPaymentIdempotency(() -> jedis);

        AutoCloseable lock1 = idempotency.acquireOrderLock(ORDER);
        assertNotNull(lock1);

        // Second lock attempt on same order fails
        AutoCloseable lock2 = idempotency.acquireOrderLock(ORDER);
        assertNull(lock2);

        // Release first lock
        lock1.close();

        // Now lock can be acquired again
        AutoCloseable lock3 = idempotency.acquireOrderLock(ORDER);
        assertNotNull(lock3);
        lock3.close();
    }
}
