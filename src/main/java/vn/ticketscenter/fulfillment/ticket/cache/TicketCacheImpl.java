package vn.ticketscenter.fulfillment.ticket.cache;

import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;

import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisScan;

/**
 * Redis cache for ticket details and QR PNG images (LIEM-05).
 * - detail key: tc:ticket:detail:v1:<id>
 * - Key QR: tc:ticket:qr:v1:<id>:<version>
 * - TTL: 1800 seconds (30 minutes).
 * - falls back when Redis is offline; the database is always the source of truth.
 */
public final class TicketCacheImpl implements TicketCache {
    public static final int DEFAULT_TTL_SECONDS = 1800;

    private final Supplier<Jedis> connections;

    public TicketCacheImpl(Supplier<Jedis> connections) {
        this.connections = Objects.requireNonNull(connections, "connections supplier is required");
    }

    public static String detailKey(UUID ticketId) {
        return "tc:ticket:detail:v1:" + ticketId;
    }

    public static String qrKey(UUID ticketId, long version) {
        return "tc:ticket:qr:v1:" + ticketId + ":" + version;
    }

    /** Drops a ticket's detail and every QR version, on the caller's connection (refund, check-in). */
    public static void evict(Jedis redis, UUID ticketId) {
        redis.del(detailKey(ticketId));
        RedisScan.deleteMatching(redis, "tc:ticket:qr:v1:" + ticketId + ":*");
    }

    /** Drops a buyer's cached ticket list. */
    public static void evictUserTickets(Jedis redis, UUID actorId) {
        RedisScan.deleteMatching(redis, "tc:user:tickets:v1:" + actorId + ":*");
    }

    /** Drops every cached ticket projection (event cancellation). */
    public static void evictAll(Jedis redis) {
        for (String pattern :
                List.of("tc:ticket:detail:v1:*", "tc:ticket:qr:v1:*", "tc:user:tickets:v1:*")) {
            RedisScan.deleteMatching(redis, pattern);
        }
    }

    @Override
    public Optional<TicketDetailDto> getTicketDetail(UUID ticketId) {
        if (ticketId == null) {
            return Optional.empty();
        }
        try (Jedis redis = connections.get()) {
            String value = redis.get(detailKey(ticketId));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(decodeDetail(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void putTicketDetail(TicketDetailDto ticket) {
        if (ticket == null) {
            return;
        }
        try (Jedis redis = connections.get()) {
            redis.setex(detailKey(ticket.id()), DEFAULT_TTL_SECONDS, ticket.toJson());
        } catch (Exception ignored) {
            // Redis only speeds up reads
        }
    }

    @Override
    public Optional<byte[]> getTicketQrPng(UUID ticketId, long version) {
        if (ticketId == null) {
            return Optional.empty();
        }
        try (Jedis redis = connections.get()) {
            String value = redis.get(qrKey(ticketId, version));
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Base64.getDecoder().decode(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void putTicketQrPng(UUID ticketId, long version, byte[] pngBytes) {
        if (ticketId == null || pngBytes == null || pngBytes.length == 0) {
            return;
        }
        try (Jedis redis = connections.get()) {
            String encoded = Base64.getEncoder().encodeToString(pngBytes);
            redis.setex(qrKey(ticketId, version), DEFAULT_TTL_SECONDS, encoded);
        } catch (Exception ignored) {
            // a cache failure must not block the request
        }
    }

    @Override
    public void invalidate(UUID ticketId) {
        if (ticketId == null) {
            return;
        }
        try (Jedis redis = connections.get()) {
            evict(redis, ticketId);
        } catch (Exception ignored) {
            // a cache failure does not interrupt the request
        }
    }

    private static TicketDetailDto decodeDetail(String json) {
        JSONObject obj = new JSONObject(json);
        return new TicketDetailDto(
                UUID.fromString(obj.getString("id")),
                UUID.fromString(obj.getString("orderId")),
                obj.optString("orderCode", ""),
                obj.getString("ticketCode"),
                obj.has("eventId") && !obj.getString("eventId").isBlank()
                        ? UUID.fromString(obj.getString("eventId"))
                        : null,
                obj.getString("eventTitle"),
                obj.optString("venueName", ""),
                obj.optString("venueAddress", ""),
                obj.has("startTime") && !obj.getString("startTime").isBlank()
                        ? Instant.parse(obj.getString("startTime"))
                        : null,
                obj.has("endTime") && !obj.getString("endTime").isBlank()
                        ? Instant.parse(obj.getString("endTime"))
                        : null,
                obj.optString("zoneName", ""),
                obj.optString("seatLabel", ""),
                obj.getLong("paidAmount"),
                obj.getString("status"),
                obj.has("issuedAt") && !obj.getString("issuedAt").isBlank()
                        ? Instant.parse(obj.getString("issuedAt"))
                        : null,
                obj.optLong("version", 0));
    }
}
