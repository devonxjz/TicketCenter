package vn.ticketscenter.fulfillment.refund.cache;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TC_ADMIN_REFUND_TEST",matches="true")
class AdminRefundCacheImplTest {
    private static Jedis connection(){String host=System.getenv("REDIS_HOST"),port=System.getenv("REDIS_PORT");
        if(!"127.0.0.1".equals(host))throw new IllegalStateException("Loopback Redis fixture required");return new Jedis(host,Integer.parseInt(port));}
    @Test void committedDecisionInvalidatesOnlyAffectedTicketsAndDurableResultCacheHasOneDayTtl(){
        UUID admin=UUID.randomUUID(),key=UUID.randomUUID(),buyer=UUID.randomUUID(),refund=UUID.randomUUID(),ticket=UUID.randomUUID(),other=UUID.randomUUID();
        var result=new AdminRefundMutationDto(refund,"APPROVED","PROCESSING",UUID.randomUUID(),buyer,List.of(ticket));
        var affected=List.of("tc:refund:detail:v1:"+refund,"tc:ticket:detail:v1:"+ticket,"tc:ticket:qr:v1:"+ticket+":1","tc:ticket:qr:v1:"+ticket+":2","tc:user:tickets:v1:"+buyer+":1");
        var unrelated="tc:ticket:detail:v1:"+other;
        try(var redis=connection()){
            for(String entry:affected)redis.setex(entry,300,"fixture");redis.setex(unrelated,300,"fixture");
            try{new AdminRefundCacheImpl(AdminRefundCacheImplTest::connection).committed(admin,key,result);
                for(String entry:affected)assertFalse(redis.exists(entry),entry);assertTrue(redis.exists(unrelated));
                assertEquals(result.json(),redis.get(AdminRefundCacheImpl.key(admin,key)));assertTrue(redis.ttl(AdminRefundCacheImpl.key(admin,key))>=86390);
            }finally{for(String entry:affected)redis.del(entry);redis.del(unrelated,AdminRefundCacheImpl.key(admin,key));}
        }
    }
}
