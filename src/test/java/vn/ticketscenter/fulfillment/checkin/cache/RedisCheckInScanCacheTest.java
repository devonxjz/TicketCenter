package vn.ticketscenter.fulfillment.checkin.cache;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.Jedis;
import java.time.Instant;
import java.util.UUID;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="TC_SCAN_INTEGRATION",matches="true")
class RedisCheckInScanCacheTest {
    private static Jedis redis(){return new Jedis("127.0.0.1",Integer.parseInt(System.getenv("REDIS_PORT")));}
    @Test void lockHasFiveSecondTtlAndOnlyItsOwnerMayRelease(){
        UUID ticket=UUID.randomUUID();String key="tc:lock:ticket-checkin:"+ticket;var cache=new RedisCheckInScanCache(RedisCheckInScanCacheTest::redis);
        try(var r=redis()){
            var first=cache.lock(ticket);String token=r.get(key);assertNotNull(token);assertTrue(r.ttl(key)>0&&r.ttl(key)<=5);
            cache.lock(ticket).close();assertEquals(token,r.get(key));r.setex(key,5,"new-owner");first.close();assertEquals("new-owner",r.get(key));r.del(key);
        }
    }
    @Test void successfulCommitWritesHourReplayAndInvalidatesQrAndEventCounts(){
        UUID actor=UUID.randomUUID(),request=UUID.randomUUID(),ticket=UUID.randomUUID(),org=UUID.randomUUID();
        var result=new CheckInScanResponseDto("CHECKED_IN_SUCCESS",ticket,"CODE","Khách","Khu A",null,Instant.EPOCH,Instant.EPOCH,org);
        String qr="tc:ticket:qr:v1:"+ticket+":image",count="tc:checkin:events:v1:"+actor+":"+org,detail="tc:ticket:detail:v1:"+ticket,idem="tc:idem:checkin:"+actor+":"+request;
        try(var r=redis()){r.set(qr,"fixture");r.set(count,"fixture");r.set(detail,"fixture");
            new RedisCheckInScanCache(RedisCheckInScanCacheTest::redis).completed(actor,request,result);
            assertNull(r.get(qr));assertNull(r.get(count));assertNull(r.get(detail));assertEquals(result.toJson(),r.get(idem));assertTrue(r.ttl(idem)>3500&&r.ttl(idem)<=3600);r.del(idem);
        }
    }
    @Test void redisOutageDoesNotRejectDatabaseResult(){var cache=new RedisCheckInScanCache(()->{throw new IllegalStateException("Fixture outage");});assertDoesNotThrow(()->cache.lock(UUID.randomUUID()).close());assertDoesNotThrow(()->cache.completed(UUID.randomUUID(),UUID.randomUUID(),new CheckInScanResponseDto("TICKET_NOT_FOUND",null,null,null,null,null,null,Instant.EPOCH,null)));}
}
