package vn.ticketscenter.ticketing.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.ticketing.dto.CreateHoldRequest;
import vn.ticketscenter.ticketing.dto.HoldMutationFingerprint;
import vn.ticketscenter.ticketing.dto.HoldSelection;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RedisHoldReconciliationIT extends HoldRedisFixture {
    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final RedisHoldIdempotency.CompletedResponse RESPONSE = new RedisHoldIdempotency.CompletedResponse(201, "application/json", "{\"saved\":true}");
    private static final Duration RETENTION = Duration.ofMinutes(10);

    @Test
    void anotherProcessCanReconcileConfirmedResultWithoutOriginalClaim() {
        String key = key();
        var firstProcess = new RedisHoldIdempotency(pool);
        var oldClaim = acquired(firstProcess, key);
        var secondProcess = new RedisHoldIdempotency(pool);
        assertTrue(secondProcess.recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        assertEquals(RESPONSE, assertInstanceOf(RedisHoldIdempotency.Replay.class, firstProcess.acquire(actor, key, HASH)).response());
        assertFalse(firstProcess.complete(oldClaim, RESPONSE, RETENTION));
        try (Jedis jedis = pool.getResource()) { assertTrue(jedis.pttl(redisKey(key)) > 0); }
    }

    @Test
    void absentEntryRemainsAbsentAndDoesNotAuthorizeAnotherClaim() {
        String key = key();
        assertFalse(new RedisHoldIdempotency(pool).recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        try (Jedis jedis = pool.getResource()) { assertFalse(jedis.exists(redisKey(key))); }
    }

    @Test
    void mismatchedPayloadCannotReplacePendingOrCompletedResult() {
        String key = key();
        var store = new RedisHoldIdempotency(pool);
        var claim = acquired(store, key);
        assertFalse(store.recoverConfirmedResult(actor, key, OTHER_HASH, RESPONSE, RETENTION));
        assertInstanceOf(RedisHoldIdempotency.InProgress.class, store.acquire(actor, key, HASH));
        assertTrue(store.complete(claim, RESPONSE, RETENTION));
        assertFalse(store.recoverConfirmedResult(actor, key, OTHER_HASH, RESPONSE, RETENTION));
        assertEquals(RESPONSE, assertInstanceOf(RedisHoldIdempotency.Replay.class, store.acquire(actor, key, HASH)).response());
    }

    @Test
    void completedResultAcceptsOnlyExactResponseWithoutExtendingRetention() {
        String key = key();
        var store = new RedisHoldIdempotency(pool);
        assertTrue(store.complete(acquired(store, key), RESPONSE, Duration.ofSeconds(30)));
        long before;
        try (Jedis jedis = pool.getResource()) { before = jedis.pttl(redisKey(key)); }
        assertTrue(store.recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        for (var changed : List.of(new RedisHoldIdempotency.CompletedResponse(200, RESPONSE.contentType(), RESPONSE.body()),
                new RedisHoldIdempotency.CompletedResponse(201, "text/plain", RESPONSE.body()),
                new RedisHoldIdempotency.CompletedResponse(201, RESPONSE.contentType(), "changed"))) {
            assertFalse(store.recoverConfirmedResult(actor, key, HASH, changed, RETENTION));
        }
        try (Jedis jedis = pool.getResource()) { assertTrue(jedis.pttl(redisKey(key)) <= before); }
    }

    @Test
    void changedPendingTokenBetweenObservationAndCasCannotBeOverwritten() {
        String key = key();
        var store = new RedisHoldIdempotency(pool);
        acquired(store, key);
        String replacement = "1|P|" + HASH + "|" + UUID.randomUUID();
        var racingStore = new RedisHoldIdempotency(() -> new Jedis(host, port) {
            @Override public Object eval(String script, List<String> scriptKeys, List<String> args) {
                set(redisKey(key), replacement);
                return super.eval(script, scriptKeys, args);
            }
        });
        assertFalse(racingStore.recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        try (Jedis jedis = pool.getResource()) {
            assertEquals(replacement, jedis.get(redisKey(key)));
            assertEquals(-1L, jedis.pttl(redisKey(key)));
        }
    }

    @Test
    void createAndCancelOnSameActorKeyConflict() {
        String key = key();
        var store = new RedisHoldIdempotency(pool);
        UUID event = UUID.randomUUID();
        var request = new CreateHoldRequest(event, List.of(new HoldSelection(UUID.randomUUID(), null, 1)));
        String create = HoldMutationFingerprint.create(request);
        String cancel = HoldMutationFingerprint.cancel(event);
        var claim = assertInstanceOf(RedisHoldIdempotency.Acquired.class, store.acquire(actor, key, create)).claim();
        assertInstanceOf(RedisHoldIdempotency.Conflict.class, store.acquire(actor, key, cancel));
        assertTrue(store.complete(claim, RESPONSE, RETENTION));
        assertInstanceOf(RedisHoldIdempotency.Conflict.class, store.acquire(actor, key, cancel));
    }

    @Test
    void corruptAndUnavailableRedisFailWithoutLeakingProviderCause() {
        String key = key();
        try (Jedis jedis = pool.getResource()) { jedis.set(redisKey(key), "corrupt"); }
        assertThrows(HoldIdempotencyUnavailableException.class,
                () -> new RedisHoldIdempotency(pool).recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        var unavailable = new RedisHoldIdempotency(() -> { throw new IllegalStateException("private provider data"); });
        var failure = assertThrows(HoldIdempotencyUnavailableException.class,
                () -> unavailable.recoverConfirmedResult(actor, key, HASH, RESPONSE, RETENTION));
        assertEquals("Hold idempotency is unavailable", failure.getMessage());
        assertNull(failure.getCause());
    }

    private String key() { String key = "it-" + UUID.randomUUID(); keys.add(redisKey(key)); return key; }
    private String redisKey(String key) { return RedisHoldIdempotency.redisKey(actor, key); }
    private RedisHoldIdempotency.Claim acquired(RedisHoldIdempotency store, String key) {
        return assertInstanceOf(RedisHoldIdempotency.Acquired.class, store.acquire(actor, key, HASH)).claim();
    }
}
