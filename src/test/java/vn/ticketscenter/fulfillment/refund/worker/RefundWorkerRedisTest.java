package vn.ticketscenter.fulfillment.refund.worker;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.fulfillment.refund.cache.RefundLeaseImpl;import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="TC_REFUND_WORKER_REDIS_TEST",matches="true")
class RefundWorkerRedisTest {
 static Jedis redis(){
  String host=System.getenv("REDIS_HOST");if(!"127.0.0.1".equals(host))throw new IllegalStateException("Loopback Redis required");
  return new Jedis(host,Integer.parseInt(System.getenv("REDIS_PORT")));
 }
 @Test void leaseIsExclusiveAndHas120SecondTtl(){
  UUID id=UUID.randomUUID();var lease=new RefundLeaseImpl(RefundWorkerRedisTest::redis);String key=RefundLeaseImpl.key(id);
  try(var r=redis()){assertEquals("PONG",r.ping());try(var handle=lease.acquire(id).orElseThrow()){
   assertTrue(r.ttl(key)>115&&r.ttl(key)<=120);assertTrue(lease.acquire(id).isEmpty());
  }assertFalse(r.exists(key));}finally{try(var r=redis()){r.del(key);}}
 }
 @Test void oldOwnerCannotDeleteSuccessorLease(){
  UUID id=UUID.randomUUID();var lease=new RefundLeaseImpl(RefundWorkerRedisTest::redis);String key=RefundLeaseImpl.key(id);var old=lease.acquire(id).orElseThrow();
  try(var r=redis()){r.del(key);try(var successor=lease.acquire(id).orElseThrow()){String token=r.get(key);old.close();assertEquals(token,r.get(key));}}finally{old.close();try(var r=redis()){r.del(key);}}
 }
}
