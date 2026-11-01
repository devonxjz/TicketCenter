package vn.ticketscenter.fulfillment.checkin.cache;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository.EventInfo;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckInEventCacheImplTest {
    private final UUID actor=UUID.randomUUID(),org=UUID.randomUUID(),event=UUID.randomUUID();
    private final FakeJedis redis=new FakeJedis();
    private final CheckInEventCacheImpl cache=new CheckInEventCacheImpl(()->redis);
    private EventInfo event(){return new EventInfo(event,org,"Nhạc \"Việt\"\n<script>","Nhà hát",Instant.parse("2026-11-15T19:00:00Z"),Instant.parse("2026-11-15T22:00:00Z"),"PUBLISHED",580,142);}
    @Test void listAndWindowRoundTripWithExactKeysAndTtls(){
        cache.putEvents(actor,org,List.of(event()));assertEquals(60,redis.ttl);
        assertEquals("tc:checkin:events:v1:"+actor+":"+org,redis.lastKey);
        assertEquals(List.of(event()),cache.events(actor,org).orElseThrow());
        cache.putWindow(event());assertEquals(30,redis.ttl);
        assertEquals("tc:checkin:window:v1:"+event,redis.lastKey);
        assertEquals(event(),cache.window(event).orElseThrow());
        assertFalse(redis.values.values().stream().anyMatch(v->v.contains("paidAmount")||v.contains("revenue")||v.contains("price")));
    }
    @Test void redisFailureDegradesToDatabaseCacheMiss(){
        CheckInEventCacheImpl unavailable=new CheckInEventCacheImpl(()->{throw new IllegalStateException("offline");});
        assertTrue(unavailable.events(actor,org).isEmpty());assertTrue(unavailable.window(event).isEmpty());
        assertDoesNotThrow(()->unavailable.putEvents(actor,org,List.of(event())));
        assertDoesNotThrow(()->unavailable.putWindow(event()));assertDoesNotThrow(()->unavailable.invalidate(event,org));
    }
    @Test void malformedOrOversizedCacheDataIsNeverTrusted(){
        redis.values.put(CheckInEventCacheImpl.windowKey(event),"invalid");assertTrue(cache.window(event).isEmpty());
        redis.values.put(CheckInEventCacheImpl.windowKey(event),"x".repeat(16385));assertTrue(cache.window(event).isEmpty());
        redis.values.put(CheckInEventCacheImpl.eventsKey(actor,org),"{\"eventId\":\"invalid\"}");assertTrue(cache.events(actor,org).isEmpty());
    }
    @Test void actorAndOrganizationKeysAreIsolated(){
        cache.putEvents(actor,org,List.of(event()));
        assertTrue(cache.events(UUID.randomUUID(),org).isEmpty());assertTrue(cache.events(actor,UUID.randomUUID()).isEmpty());
    }
    private static final class FakeJedis extends Jedis {
        final Map<String,String> values=new HashMap<>();long ttl;String lastKey;
        @Override public String get(String key){return values.get(key);}
        @Override public String setex(String key,long seconds,String value){lastKey=key;ttl=seconds;values.put(key,value);return "OK";}
        @Override public void close(){}
    }
}
