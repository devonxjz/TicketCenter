package vn.ticketscenter.order.cache;

import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.config.redis.RedisScan;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.dao.OrderRepository;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;

public final class RedisOrderCache {
    private static final long MAX_TTL = 600_000;
    private static final int MAX_PAYLOAD = 64 * 1024;
    private static final String FILL =
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local ttl = math.min(600000, tonumber(ARGV[4]) - now)
            if ttl < 1 then return 0 end
            redis.call('PSETEX', KEYS[2], ttl, ARGV[2])
            redis.call('PSETEX', KEYS[3], ttl, ARGV[3])
            return 1
            """;
    private static final String INVALIDATE =
            """
            redis.call('PSETEX', KEYS[1], 600000, ARGV[1])
            local order = redis.call('GET', KEYS[2])
            if order then redis.call('DEL', 'tc:order:summary:' .. order) end
            redis.call('DEL', KEYS[2])
            redis.call('DEL', KEYS[3])
            return 1
            """;
    private final Supplier<Jedis> connections;
    private final Clock clock;

    public RedisOrderCache(Supplier<Jedis> connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.clock = Objects.requireNonNull(clock);
    }

    public record FillToken(UUID actorId, UUID holdId, String marker) {}

    public record Lookup(Optional<CurrentCheckout.CurrentOrder> order, Optional<FillToken> token) {}

    public Lookup lookup(UUID actorId, UUID holdId) {
        try (Jedis jedis = connections.get()) {
            String fence = fenceKey(actorId, holdId), marker = jedis.get(fence);
            if (marker == null) {
                marker = UUID.randomUUID().toString();
                if (!"OK".equals(jedis.set(fence, marker, SetParams.setParams().nx().px(MAX_TTL))))
                    marker = jedis.get(fence);
            }
            if (marker == null || !marker.matches("[0-9a-f-]{36}")) return miss();
            Optional<CurrentCheckout.CurrentOrder> found = Optional.empty();
            String mapped = jedis.get(mappingKey(actorId, holdId));
            if (mapped != null && mapped.matches("[0-9a-f-]{36}")) {
                String body = jedis.get(summaryKey(UUID.fromString(mapped)));
                if (body != null && body.length() <= MAX_PAYLOAD) {
                    JSONObject root =
                            new JSONObject()
                                    .put("serverTime", clock.instant().toString())
                                    .put("order", new JSONObject(body));
                    var decoded = OrderRepository.checkout(root.toString()).order();
                    if (decoded.isPresent()
                            && decoded.get().snapshot().buyerId().equals(actorId)
                            && decoded.get().snapshot().holdId().equals(holdId)
                            && decoded.get().snapshot().orderId().toString().equals(mapped)
                            && clock.instant().isBefore(decoded.get().snapshot().expiresAt()))
                        found = decoded;
                }
            }
            return new Lookup(found, Optional.of(new FillToken(actorId, holdId, marker)));
        } catch (RuntimeException unavailable) {
            return miss();
        }
    }

    public boolean fill(FillToken token, CurrentCheckout.CurrentOrder order) {
        if (token == null
                || !token.actorId().equals(order.snapshot().buyerId())
                || !token.holdId().equals(order.snapshot().holdId())
                || !order.canEditCoupon()
                || order.body().length() > MAX_PAYLOAD) return false;
        try (Jedis jedis = connections.get()) {
            return Long.valueOf(1)
                    .equals(
                            jedis.eval(
                                    FILL,
                                    List.of(
                                            fenceKey(token.actorId(), token.holdId()),
                                            mappingKey(token.actorId(), token.holdId()),
                                            summaryKey(order.snapshot().orderId())),
                                    List.of(
                                            token.marker(),
                                            order.snapshot().orderId().toString(),
                                            order.body(),
                                            Long.toString(
                                                    order.snapshot().expiresAt().toEpochMilli()))));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public boolean invalidateAfterCommit(UUID actorId, UUID holdId, UUID orderId) {
        try (Jedis jedis = connections.get()) {
            return Long.valueOf(1)
                    .equals(
                            jedis.eval(
                                    INVALIDATE,
                                    List.of(
                                            fenceKey(actorId, holdId),
                                            mappingKey(actorId, holdId),
                                            summaryKey(orderId)),
                                    List.of(UUID.randomUUID().toString())));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public boolean saveReceiptAfterCommit(
            OrderActor actor, OrderCommand command, OrderMutationReceipt receipt) {
        long ttl =
                Math.min(
                        MAX_TTL, Duration.between(clock.instant(), receipt.expiresAt()).toMillis());
        if (ttl < 1 || receipt.body().length() > MAX_PAYLOAD) return false;
        JSONObject value =
                new JSONObject()
                        .put("fingerprint", command.fingerprint(actor.id()))
                        .put("httpStatus", receipt.httpStatus())
                        .put("body", receipt.body());
        try (Jedis jedis = connections.get()) {
            return "OK"
                    .equals(
                            jedis.set(
                                    receiptKey(actor.id(), command.key()),
                                    value.toString(),
                                    SetParams.setParams().px(ttl)));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Xóa order summary đã cache của mọi đơn (hủy sự kiện). */
    public static void evictAllSummaries(Jedis redis) {
        RedisScan.deleteMatching(redis, "tc:order:summary:*");
    }

    public static String summaryKey(UUID orderId) {
        return "tc:order:summary:" + orderId;
    }

    public static String receiptKey(UUID actorId, UUID key) {
        return "tc:idem:order:" + actorId + ":" + key;
    }

    public static String fenceKey(UUID actorId, UUID holdId) {
        return "tc:order:fence:" + actorId + ":" + holdId;
    }

    public static String mappingKey(UUID actorId, UUID holdId) {
        return "tc:order:checkout:" + actorId + ":" + holdId;
    }

    private static Lookup miss() {
        return new Lookup(Optional.empty(), Optional.empty());
    }
}
