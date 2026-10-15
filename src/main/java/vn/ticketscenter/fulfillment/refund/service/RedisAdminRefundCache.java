package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;

public final class RedisAdminRefundCache implements AdminRefundCache {
    public static final int IDEMPOTENCY_TTL=86400;
    private final Supplier<Jedis> connections;
    public RedisAdminRefundCache() { this(()->RedisClientProvider.getInstance().getResource()); }
    public RedisAdminRefundCache(Supplier<Jedis> connections) { this.connections=connections; }
    public static String key(UUID admin,UUID key) { return "tc:idem:refund-decision:"+admin+":"+key; }
    @Override public void committed(UUID admin,UUID key,AdminRefundMutationDto result) {
        try(var redis=connections.get()) {
            redis.del("tc:refund:detail:v1:"+result.refundId(),"tc:admin:pending_refunds_count");
            remove(redis,"tc:user:tickets:v1:"+result.buyerId()+":*");
            for(UUID ticket:result.ticketIds()) {
                redis.del("tc:ticket:detail:v1:"+ticket);
                remove(redis,"tc:ticket:qr:v1:"+ticket+":*");
            }
            redis.setex(key(admin,key),IDEMPOTENCY_TTL,result.json());
        }
    }
    private static void remove(Jedis redis,String pattern) {
        String cursor="0";
        do {var page=redis.scan(cursor,new ScanParams().match(pattern).count(100));if(!page.getResult().isEmpty())redis.del(page.getResult().toArray(String[]::new));cursor=page.getCursor();}while(!"0".equals(cursor));
    }
}
