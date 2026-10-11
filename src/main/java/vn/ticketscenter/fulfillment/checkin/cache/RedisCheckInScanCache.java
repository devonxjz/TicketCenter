package vn.ticketscenter.fulfillment.checkin.cache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.params.ScanParams;
import java.util.*;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanResponseDto;
public final class RedisCheckInScanCache implements CheckInScanCache {
    private final Supplier<Jedis> connections;
    public RedisCheckInScanCache(){this(()->RedisClientProvider.getInstance().getResource());}
    public RedisCheckInScanCache(Supplier<Jedis> connections){this.connections=connections;}
    @Override public Lease lock(UUID ticketId){
        if(ticketId==null)return ()->{};
        String key="tc:lock:ticket-checkin:"+ticketId,token=UUID.randomUUID().toString();
        try(Jedis r=connections.get()){
            if(!"OK".equals(r.set(key,token,SetParams.setParams().nx().ex(5))))return ()->{};
        }catch(RuntimeException ignored){return ()->{};}
        return ()->{try(Jedis r=connections.get()){
            r.eval("if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",List.of(key),List.of(token));
        }catch(RuntimeException ignored){}};
    }
    @Override public void completed(UUID actor,UUID key,CheckInScanResponseDto result){
        try(Jedis r=connections.get()){
            r.setex("tc:idem:checkin:"+actor+":"+key,3600,result.toJson());
            if(!result.success())return;
            r.del("tc:ticket:detail:v1:"+result.ticketId());
            for(String pattern:List.of("tc:ticket:qr:v1:"+result.ticketId()+":*","tc:checkin:events:v1:*:"+result.organizationId())){
                String cursor="0";do{var scan=r.scan(cursor,new ScanParams().match(pattern).count(100));
                    if(!scan.getResult().isEmpty())r.del(scan.getResult().toArray(String[]::new));cursor=scan.getCursor();
                }while(!cursor.equals("0"));
            }
        }catch(RuntimeException ignored){/* SQL preserves correctness and durable replays during Redis outages. */}
    }
}
