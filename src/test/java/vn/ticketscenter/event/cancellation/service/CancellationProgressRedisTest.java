package vn.ticketscenter.event.cancellation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.*;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.event.cancellation.cache.CancellationProgressCache;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TC_CANCELLATION_REDIS_TEST",matches="true")
class CancellationProgressRedisTest {
    static Jedis redis() {
        String host=System.getenv("REDIS_HOST");
        if(!"127.0.0.1".equals(host))throw new IllegalStateException("Loopback Redis fixture required");
        return new Jedis(host,Integer.parseInt(System.getenv("REDIS_PORT")),2000);
    }
    @Test void progressHasTenSecondTtlAndHitsDoNotRenewIt()throws Exception {
        var helper=new CancellationProgressCache(CancellationProgressRedisTest::redis);var p=EventCancellationServiceTest.result();String key="tc:event:cancel-progress:"+p.eventId();
        try {helper.publish(p);try(var r=redis()){assertTrue(r.ttl(key)>0&&r.ttl(key)<=10);}
            Thread.sleep(1100);assertEquals(p.json(),helper.get(p.eventId()).orElseThrow());
            try(var r=redis()){assertTrue(r.ttl(key)<=9);}
        }finally{try(var r=redis()){r.del(key);}}
    }
    @Test void corruptionWrongEventAndRedisOutageFallBackToSql() {
        var helper=new CancellationProgressCache(CancellationProgressRedisTest::redis);var p=EventCancellationServiceTest.result();String key="tc:event:cancel-progress:"+p.eventId();
        try(var r=redis()) {for(String json:List.of("broken",p.json().replace(p.eventId().toString(),UUID.randomUUID().toString()),p.json().replace("\"processedOrders\":2","\"processedOrders\":500"),
                p.json().replace("\"processedOrders\":2","\"processedOrders\":\"2\""),p.json().replace("\"progressPercentage\":50.0","\"progressPercentage\":90.0"))) {
            r.setex(key,10,json);assertTrue(helper.get(p.eventId()).isEmpty());
        }r.del(key);}
        assertTrue(new CancellationProgressCache(()->{throw new IllegalStateException("Down");}).get(p.eventId()).isEmpty());
    }
    @Test void committedCancellationInvalidatesDisplaysAndPreservesLeasesAndReceipts() {
        var helper=new CancellationProgressCache(CancellationProgressRedisTest::redis);var p=EventCancellationServiceTest.result();String id=p.eventId().toString();
        var deleted=List.of("tc:event:detail:v1:"+id,"tc:event:list:v1:"+id,"tc:checkin:window:v1:"+id,"tc:user:active-hold:"+id,"tc:ticket:detail:v1:"+id,"tc:user:tickets:v1:"+id+":1");
        var retained=List.of("tc:idem:refund-request:"+id,"tc:lease:refund:"+id,"tc:lock:ticket-checkin:"+id,"tc:order:fence:"+id);
        try(var r=redis()){for(String key:deleted)r.setex(key,30,"fixture");for(String key:retained)r.setex(key,30,"fixture");}
        try{helper.cancelled(p);try(var r=redis()){for(String key:deleted)assertNull(r.get(key),key);for(String key:retained)assertEquals("fixture",r.get(key),key);}}
        finally{try(var r=redis()){for(String key:deleted)r.del(key);for(String key:retained)r.del(key);}}
    }
}
