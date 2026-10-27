package vn.ticketscenter.settlement.cache;
import vn.ticketscenter.config.redis.RedisKeyValueBackend;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class SettlementReadCacheTest {
    @Test void cachesByExactEventAndKindWithSpecifiedTtlAndInvalidatesBoth() {
        var values=new HashMap<String,String>();var ttls=new HashMap<String,Integer>();
        var cache=new SettlementReadCache(new RedisKeyValueBackend(){
            public String get(String key){return values.get(key);}
            public void setex(String key,int ttl,String value){values.put(key,value);ttls.put(key,ttl);}
            public void delete(String key){values.remove(key);}
        });
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();var reads=new AtomicInteger();
        assertEquals("summary",cache.read(first,SettlementReadCache.Kind.SUMMARY,()->{reads.incrementAndGet();return "summary";}));
        assertEquals("summary",cache.read(first,SettlementReadCache.Kind.SUMMARY,()->{throw new AssertionError("Expected scoped hit");}));
        assertEquals("blockers",cache.read(first,SettlementReadCache.Kind.BLOCKERS,()->"blockers"));
        assertEquals("other",cache.read(second,SettlementReadCache.Kind.SUMMARY,()->"other"));
        assertEquals(60,ttls.get("tc:settlement:summary:v1:"+first));assertEquals(30,ttls.get("tc:settlement:blockers:v1:"+first));assertEquals(1,reads.get());
        cache.invalidate(first);assertFalse(values.containsKey("tc:settlement:summary:v1:"+first));assertFalse(values.containsKey("tc:settlement:blockers:v1:"+first));assertEquals("other",values.get("tc:settlement:summary:v1:"+second));
    }
    @Test void outagesNeverPreventAuthoritativeReadOrCommittedInvalidation() {
        var cache=new SettlementReadCache(new RedisKeyValueBackend(){public String get(String k){throw new IllegalStateException();}public void setex(String k,int ttl,String v){throw new IllegalStateException();}public void delete(String k){throw new IllegalStateException();}});
        UUID id=UUID.randomUUID();var calls=new AtomicInteger();assertEquals("sql",cache.read(id,SettlementReadCache.Kind.SUMMARY,()->{calls.incrementAndGet();return "sql";}));assertEquals(1,calls.get());assertDoesNotThrow(()->cache.invalidate(id));
    }
}
