package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.*;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;

/** Redis reduces contention and clears display caches; SQL owns all financial decisions. */
public final class RedisRefundRequestCache implements RefundRequestCache {
    public static final int IDEMPOTENCY_TTL=86400;
    private final Supplier<Jedis> connections;
    public RedisRefundRequestCache() { this(() -> RedisClientProvider.getInstance().getResource()); }
    public RedisRefundRequestCache(Supplier<Jedis> connections) { this.connections=connections; }
    public static String lockKey(List<UUID> ids) { return "tc:lock:refund-tickets:"+ids.stream().map(UUID::toString).sorted().collect(Collectors.joining("_")); }
    public static String idemKey(UUID actor,UUID key) { return "tc:idem:refund-request:"+actor+":"+key; }
    @Override public Lease lock(List<UUID> ids) {
        String key=lockKey(ids),token=UUID.randomUUID().toString();
        try(var redis=connections.get()) {
            if(!"OK".equals(redis.set(key,token,SetParams.setParams().nx().px(10000)))) return () -> {};
        } catch(RuntimeException unavailable) { return () -> {}; }
        return () -> {try(var redis=connections.get()) {
            redis.eval("if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",List.of(key),List.of(token));
        } catch(RuntimeException ignored) { }};
    }
    @Override public void committed(UUID actor,UUID key,List<UUID> tickets,RefundSummaryDto result) {
        try(var redis=connections.get()) {
            for(UUID ticket:tickets) {
                redis.del("tc:ticket:detail:v1:"+ticket);
                remove(redis,"tc:ticket:qr:v1:"+ticket+":*");
            }
            remove(redis,"tc:user:tickets:v1:"+actor+":*");
            redis.del("tc:admin:pending_refunds_count");
            redis.setex(idemKey(actor,key),IDEMPOTENCY_TTL,result.json());
        } catch(RuntimeException ignored) { /* The mutation has already committed; never report it as failed. */ }
    }
    private static void remove(Jedis redis,String pattern) {
        String cursor="0";var params=new ScanParams().match(pattern).count(100);
        do {var page=redis.scan(cursor,params);if(!page.getResult().isEmpty())redis.del(page.getResult().toArray(String[]::new));cursor=page.getCursor();}while(!"0".equals(cursor));
    }
}
