package vn.ticketscenter.event.cancellation.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.redis.RedisScan;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.cache.EventCatalogCacheImpl;
import vn.ticketscenter.event.cancellation.dto.CancellationProgressDto;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInEventCacheImpl;

public class CancellationProgressCache {
    private static final String PROGRESS_PREFIX = "tc:event:cancel-progress:";
    private static final String PENDING_REFUNDS_KEY = "tc:admin:pending_refunds_count";
    private static final Set<String> PROGRESS_FIELDS =
            Set.of(
                    "eventId",
                    "status",
                    "reason",
                    "cancelledAt",
                    "totalOrders",
                    "processedOrders",
                    "progressPercentage",
                    "updatedAt",
                    "successfulRefunds",
                    "failedRefunds",
                    "unknownRefunds",
                    "pendingRefunds",
                    "usedTicketExceptions");
    private static final List<String> COUNT_FIELDS =
            List.of(
                    "totalOrders",
                    "processedOrders",
                    "successfulRefunds",
                    "failedRefunds",
                    "unknownRefunds",
                    "pendingRefunds",
                    "usedTicketExceptions");
    private static final List<String> TICKET_PROJECTIONS =
            List.of(
                    "tc:user:active-hold:*",
                    "tc:order:summary:*",
                    "tc:ticket:detail:v1:*",
                    "tc:ticket:qr:v1:*",
                    "tc:user:tickets:v1:*");
    private final Supplier<Jedis> connections;

    public CancellationProgressCache() {
        this(() -> RedisClientProvider.getInstance().getResource());
    }

    public CancellationProgressCache(Supplier<Jedis> connections) {
        this.connections = connections;
    }

    public void publish(CancellationProgressDto progress) {
        try (var redis = connections.get()) {
            redis.setex(PROGRESS_PREFIX + progress.eventId(), 10, progress.json());
        }
    }

    public Optional<String> get(UUID id) {
        try (var redis = connections.get()) {
            String json = redis.get(PROGRESS_PREFIX + id);
            if (json == null || json.length() > 8192) return Optional.empty();
            var fields = JsonObjectParser.parse(new StringReader(json));
            if (!validFields(id, fields)) return Optional.empty();

            var normalized =
                    new CancellationProgressDto(
                                    id,
                                    null,
                                    fields.get("status"),
                                    fields.get("reason"),
                                    Instant.parse(fields.get("cancelledAt")),
                                    Long.parseLong(fields.get("totalOrders")),
                                    Long.parseLong(fields.get("processedOrders")),
                                    null,
                                    Instant.parse(fields.get("updatedAt")),
                                    Long.parseLong(fields.get("successfulRefunds")),
                                    Long.parseLong(fields.get("failedRefunds")),
                                    Long.parseLong(fields.get("unknownRefunds")),
                                    Long.parseLong(fields.get("pendingRefunds")),
                                    Long.parseLong(fields.get("usedTicketExceptions")))
                            .json();
            // Reject coerced number strings and percentages that differ from SQL's typed
            // projection.
            if (!normalized.equals(json)) return Optional.empty();
            return Optional.of(json); // A hit does not renew the ten-second TTL.
        } catch (Exception unavailable) {
            return Optional.empty();
        }
    }

    public void cancelled(CancellationProgressDto progress) {
        new EventCatalogCacheImpl(connections)
                .invalidateCommitted(progress.eventId(), progress.organizationId());
        new CheckInEventCacheImpl(connections)
                .invalidate(progress.eventId(), progress.organizationId());
        try (var redis = connections.get()) {
            // Remove stale purchase/refund actions; mutations still verify their authority in SQL.
            clearTicketProjections(redis);
            redis.del(PROGRESS_PREFIX + progress.eventId(), PENDING_REFUNDS_KEY);
        }
    }

    private static boolean validFields(UUID id, Map<String, String> fields) {
        if (!fields.keySet().equals(PROGRESS_FIELDS)
                || !id.toString().equals(fields.get("eventId"))
                || !Set.of("PENDING", "IN_PROGRESS", "COMPLETED").contains(fields.get("status")))
            return false;
        for (String key : COUNT_FIELDS) {
            if (!fields.get(key).matches("[0-9]+") || Long.parseLong(fields.get(key)) < 0)
                return false;
        }
        if (Long.parseLong(fields.get("processedOrders"))
                > Long.parseLong(fields.get("totalOrders"))) return false;
        var percentage = new BigDecimal(fields.get("progressPercentage"));
        if (percentage.signum() < 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0)
            return false;
        Instant.parse(fields.get("updatedAt"));
        Instant.parse(fields.get("cancelledAt"));
        return fields.get("reason") != null && fields.get("reason").length() <= 500;
    }

    private static void clearTicketProjections(Jedis redis) {
        for (String pattern : TICKET_PROJECTIONS) {
            RedisScan.deleteMatching(redis, pattern);
        }
    }
}
