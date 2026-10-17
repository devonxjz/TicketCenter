package vn.ticketscenter.fulfillment.refund.integration;

import jakarta.persistence.*;
import java.net.ServerSocket;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.*;
import vn.ticketscenter.fulfillment.refund.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.ticketscenter.fulfillment.refund.integration.RefundFixture.*;

@EnabledIfEnvironmentVariable(named="TC_REFUND_REDIS_TESTS",matches="true")
class RefundRequestRedisTest {
    private static Jedis redis(){return new Jedis("127.0.0.1",16379,1000);}
    @Test void lockUsesSortedTicketSetTtlAndOwnershipAwareRelease(){var a=UUID.randomUUID();var b=UUID.randomUUID();var ids=List.of(a,b);var cache=new RedisRefundRequestCache(RefundRequestRedisTest::redis);String key=RedisRefundRequestCache.lockKey(ids);assertEquals(key,RedisRefundRequestCache.lockKey(List.of(b,a)));try(var r=redis()){var lease=cache.lock(ids);try{assertTrue(r.pttl(key)>0&&r.pttl(key)<=10000);r.set(key,"replacement-owner");lease.close();assertEquals("replacement-owner",r.get(key));}finally{r.del(key);}}}
    @Test void actualCommitInvalidatesTicketQrAndUserCachesAndPersistsReplayForOneDay()throws Exception{
        var f=new RefundFixture(100,false);var key=UUID.randomUUID();var cache=new RedisRefundRequestCache(RefundRequestRedisTest::redis);
        try(var preflight=RefundFixture.connect("BUYER")){assertEquals("TicketCenter_Khanh03_Test",preflight.getCatalog());}
        try(var factory=Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.jdbc.url",required("TC_REFUND_TEST_URL"),"jakarta.persistence.jdbc.user",required("TC_REFUND_BUYER_USER"),"jakarta.persistence.jdbc.password",required("TC_REFUND_BUYER_PASSWORD")));var r=redis()){
            var keys=List.of("tc:ticket:detail:v1:"+f.tickets.getFirst(),"tc:ticket:qr:v1:"+f.tickets.getFirst()+":fixture","tc:user:tickets:v1:"+f.buyer+":fixture");for(String k:keys)r.set(k,"stale");
            try{var service=new RefundRequestService(new RefundRequestRepository(new TransactionManager(Map.of(DatabasePrincipal.BUYER,factory))),cache,Clock.systemUTC());var request=new CreateRefundRequestDto(f.order,f.tickets,"Không thể tham dự sự kiện.");var first=service.create(f.buyer(),request,key);for(String k:keys)assertNull(r.get(k));String idem=RedisRefundRequestCache.idemKey(f.buyer,key);assertTrue(r.ttl(idem)>86300&&r.ttl(idem)<=86400);assertEquals(first.json(),r.get(idem));r.del(idem);assertEquals(first,service.create(f.buyer(),request,key));assertNull(r.get(RedisRefundRequestCache.lockKey(f.tickets)));assertEquals("REFUND_PENDING",scalar("SELECT status FROM dbo.tc_tickets WHERE id=?",f.tickets.getFirst()));}
            finally{for(String k:keys)r.del(k);r.del(RedisRefundRequestCache.idemKey(f.buyer,key));}
        }
    }
    @Test void realUnavailableRedisDoesNotPreventSqlCommitOrDurableReplay()throws Exception{
        int port;try(var reservation=new ServerSocket(0)){port=reservation.getLocalPort();}
        var cache=new RedisRefundRequestCache(()->new Jedis("127.0.0.1",port,100));var f=new RefundFixture(100,false);var key=UUID.randomUUID();
        try(var preflight=RefundFixture.connect("BUYER")){assertEquals("TicketCenter_Khanh03_Test",preflight.getCatalog());}
        try(var factory=Persistence.createEntityManagerFactory("ticketscenter",Map.of("jakarta.persistence.jdbc.url",required("TC_REFUND_TEST_URL"),"jakarta.persistence.jdbc.user",required("TC_REFUND_BUYER_USER"),"jakarta.persistence.jdbc.password",required("TC_REFUND_BUYER_PASSWORD")))){
            var service=new RefundRequestService(new RefundRequestRepository(new TransactionManager(Map.of(DatabasePrincipal.BUYER,factory))),cache,Clock.systemUTC());var request=new CreateRefundRequestDto(f.order,f.tickets,"Không thể tham dự sự kiện.");var first=service.create(f.buyer(),request,key);assertEquals(first,service.create(f.buyer(),request,key));assertEquals("1",scalar("SELECT COUNT(*) FROM dbo.tc_refund_obligations WHERE order_id=?",f.order));
        }
    }
}
