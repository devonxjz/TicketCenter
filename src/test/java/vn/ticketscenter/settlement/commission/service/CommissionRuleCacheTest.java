package vn.ticketscenter.settlement.commission.service;

import vn.ticketscenter.config.redis.RedisKeyValueBackend;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CommissionRuleCacheTest {
    @Test void cacheScopeAndInvalidationAvoidOldRules() {
        var values=new HashMap<String,String>(); int[] reads={0},ttl={0};
        var cache=new CommissionRuleCache(new RedisKeyValueBackend() {
            public String get(String key) { return values.get(key); }
            public void setex(String key,int seconds,String value) { ttl[0]=seconds; values.put(key,value); }
            public void delete(String key) { values.remove(key); }
        });
        UUID org=UUID.randomUUID();
        assertEquals("old",cache.read(org,()->{ reads[0]++;return "old"; }));
        assertEquals("old",cache.read(org,()->{ reads[0]++;return "new"; }));
        assertEquals(1,reads[0]); assertEquals(300,ttl[0]);
        cache.invalidate(org); assertEquals("new",cache.read(org,()->"new"));
        assertEquals("other",cache.read(UUID.randomUUID(),()->"other"));
    }
    @Test void cacheOutageFallsBackToDatabaseOnce() {
        int[] calls={0};
        var cache=new CommissionRuleCache(new RedisKeyValueBackend() {
            public String get(String k) { throw new IllegalStateException(); }
            public void setex(String k,int t,String v) { throw new IllegalStateException(); }
            public void delete(String k) { throw new IllegalStateException(); }
        });
        assertEquals("fresh",cache.read(UUID.randomUUID(),()->{ calls[0]++;return "fresh"; })); assertEquals(1,calls[0]);
        assertDoesNotThrow(()->cache.invalidate(UUID.randomUUID()));
    }
}
