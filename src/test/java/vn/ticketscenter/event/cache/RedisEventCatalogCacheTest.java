package vn.ticketscenter.event.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;
import static org.junit.jupiter.api.Assertions.*;

class RedisEventCatalogCacheTest {
    @Test void olderSuccessfulInvalidationCannotClearNewerFailedInvalidation() throws Exception {
        var scanStarted=new CountDownLatch(1);var releaseScan=new CountDownLatch(1);
        var firstScan=new AtomicBoolean(true);
        var older=new Redis() {
            @Override public long incr(String key) {return 1;}
            @Override public ScanResult<String> scan(String cursor,ScanParams params) {
                if(firstScan.getAndSet(false)) {
                    scanStarted.countDown();
                    try {assertTrue(releaseScan.await(5,TimeUnit.SECONDS));}
                    catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
                }
                return new ScanResult<>("0",List.of());
            }
        };
        var readableButCannotInvalidate=new Redis() {
            @Override public long incr(String key) {throw new IllegalStateException("Redis mutation unavailable");}
        };
        readableButCannotInvalidate.values.put("key","{\"data\":[\"stale projection\"]}");
        var calls=new AtomicInteger();var cache=new RedisEventCatalogCache(()->{
            int call=calls.incrementAndGet();if(call==1)return older;
            if(call==2)throw new IllegalStateException("Newer invalidation failed");
            return readableButCannotInvalidate;
        });
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?> first=pool.submit(()->cache.invalidateCommitted(null,null));
            try {
                assertTrue(scanStarted.await(5,TimeUnit.SECONDS));
                cache.invalidateCommitted(null,null);
            } finally {releaseScan.countDown();}
            first.get(5,TimeUnit.SECONDS);
            assertTrue(cache.get("key").isEmpty(),"A newer failed invalidation must keep reads on SQL fallback");
        }
    }
    static class Redis extends Jedis {
        final Map<String, String> values = new HashMap<>(); int ttl;
        @Override public String get(String key) { return values.get(key); }
        @Override public String setex(String key, long seconds, String value) { values.put(key, value); ttl = (int) seconds; return "OK"; }
        @Override public void close() { }
    }
    @Test void storesJsonWithTtlAndBoundsCachePayloads() {
        var redis = new Redis(); var cache = new RedisEventCatalogCache(() -> redis);
        assertTrue(cache.get("key").isEmpty()); cache.put("key", "{\"data\":[]}", 30);
        assertEquals("{\"data\":[]}", cache.get("key").orElseThrow()); assertEquals(30, redis.ttl);
        cache.put("large", "x".repeat(2_000_001), 30); assertFalse(redis.values.containsKey("large"));
        redis.values.put("key", "not JSON"); assertTrue(cache.get("key").isEmpty());
    }
    @Test void connectionFailuresDoNotEscapeReadOrWrite() {
        var cache = new RedisEventCatalogCache(() -> { throw new IllegalStateException("provider credential must stay private"); });
        assertTrue(cache.get("key").isEmpty()); assertDoesNotThrow(() -> cache.put("key", "{\"data\":[]}", 30));
    }
    @Test void corruptJsonEvenWithCorrectPrefixFallsBackToDatabase() {
        var redis = new Redis(); var cache = new RedisEventCatalogCache(() -> redis);
        for (String value : java.util.List.of("{\"data\":[}", "{\"data\":[],\"page\":01}",
                "{\"data\":[{\"title\":\"bad\\x\"}]}", "{\"data\":[]}{\"injected\":true}",
                "{\"data\":null}", "{\"data\":[1,]}", "{\"data\":[\"line\n\"]}")) {
            redis.values.put("key", value); assertTrue(cache.get("key").isEmpty(), value);
        }
        assertDoesNotThrow(() -> cache.put("broken", "{\"data\":[}", 30));
        assertFalse(redis.values.containsKey("broken"));
    }
    @Test void nestedDtoJsonWithEscapesNumbersAndNullIsAccepted() {
        var redis = new Redis(); var cache = new RedisEventCatalogCache(() -> redis);
        String value = "{\"data\":[{\"title\":\"quote \\\" and \\u0041\",\"image\":null,\"active\":false,\"n\":1.2e+3}],\"total\":1}";
        cache.put("key", value, 30); assertEquals(value, cache.get("key").orElseThrow());
    }
}
