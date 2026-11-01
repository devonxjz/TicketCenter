package vn.ticketscenter.fulfillment.refund.cache;

import java.util.*;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;
import vn.ticketscenter.fulfillment.ticket.cache.TicketCacheImpl;

public final class AdminRefundCacheImpl implements AdminRefundCache {
    public static final int IDEMPOTENCY_TTL = 86400;
    private final Supplier<Jedis> connections;

    public AdminRefundCacheImpl() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public AdminRefundCacheImpl(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    public static final String PENDING_COUNT_KEY = "tc:admin:pending_refunds_count";

    public static String detailKey(UUID refundId) {
        return "tc:refund:detail:v1:" + refundId;
    }

    public static String key(UUID admin, UUID key) {
        return "tc:idem:refund-decision:" + admin + ":" + key;
    }

    @Override
    public void committed(UUID admin, UUID key, AdminRefundMutationDto result) {
        try (Jedis redis = connections.get()) {
            redis.del(detailKey(result.refundId()), PENDING_COUNT_KEY);
            TicketCacheImpl.evictUserTickets(redis, result.buyerId());
            for (UUID ticket : result.ticketIds()) {
                TicketCacheImpl.evict(redis, ticket);
            }
            redis.setex(key(admin, key), IDEMPOTENCY_TTL, result.json());
        }
    }
}
