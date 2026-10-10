package vn.ticketscenter.event.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RedisEventCatalogCacheTest {
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
