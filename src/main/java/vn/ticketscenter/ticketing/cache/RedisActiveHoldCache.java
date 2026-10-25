package vn.ticketscenter.ticketing.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;
import vn.ticketscenter.ticketing.dto.ActiveHoldProjection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import vn.ticketscenter.config.redis.RedisScripts;

/**
 * Services must recheck current DB permission and ownership before returning hits.
 * Invalidate only after confirmed commit.
 */
public final class RedisActiveHoldCache {
    private static final int MAX_PAYLOAD_CHARS = 1024;
    private static final long MARKER_TTL_MILLIS = Duration.ofMinutes(10).toMillis();
    private static final String FILL =
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local ttl = math.min(tonumber(ARGV[3]), tonumber(ARGV[4]) - now)
            if ttl < 1 then return 0 end
            redis.call('PSETEX', KEYS[1], ttl, ARGV[2])
            return 1
            """;
    private final Supplier<Jedis> connections;
    private final Clock clock;

    public RedisActiveHoldCache(Supplier<Jedis> connections, Clock clock) {
        this.connections = Objects.requireNonNull(connections);
        this.clock = Objects.requireNonNull(clock);
    }

    public record Lookup(
            Optional<ActiveHoldProjection> projection, Optional<FillToken> fillToken) {}

    public static final class FillToken {
        private final UUID actorId;
        private final String marker;

        private FillToken(UUID actorId, String marker) {
            this.actorId = actorId;
            this.marker = marker;
        }
    }

    public Lookup lookup(UUID actorId) {
        String key = redisKey(actorId);
        try (Jedis jedis = connections.get()) {
            String observed = jedis.get(key);
            if (observed != null && observed.startsWith("1|D|")) {
                Optional<ActiveHoldProjection> projection = decode(observed.substring(4));
                if (projection.isPresent()
                        && projection.get().buyerId().equals(actorId)
                        && clock.instant().isBefore(projection.get().expiresAt())) {
                    return new Lookup(projection, Optional.empty());
                }
            } else if (validMarker(observed)) {
                return miss(new FillToken(actorId, observed));
            }
            String marker = marker();
            boolean seeded =
                    observed == null
                            ? "OK"
                                    .equals(
                                            jedis.set(
                                                    key,
                                                    marker,
                                                    SetParams.setParams()
                                                            .nx()
                                                            .px(MARKER_TTL_MILLIS)))
                            : Long.valueOf(1)
                                    .equals(
                                            jedis.eval(
                                                    RedisScripts.REPLACE_IF_EQUALS_PX,
                                                    List.of(key),
                                                    List.of(
                                                            observed,
                                                            marker,
                                                            Long.toString(MARKER_TTL_MILLIS))));
            return seeded ? miss(new FillToken(actorId, marker)) : miss(null);
        } catch (RuntimeException unavailable) {
            return miss(null);
        }
    }

    /** Snapshot must come from an ACTIVE hold read in DB, with current ownership checked by its caller. */
    public boolean fill(FillToken token, ActiveHoldProjection projection) {
        Objects.requireNonNull(projection);
        if (token == null || !token.actorId.equals(projection.buyerId())) return false;
        try (Jedis jedis = connections.get()) {
            long ttl =
                    Math.min(
                            MARKER_TTL_MILLIS,
                            Duration.between(clock.instant(), projection.expiresAt()).toMillis());
            if (ttl < 1) return false;
            return Long.valueOf(1)
                    .equals(
                            jedis.eval(
                                    FILL,
                                    List.of(redisKey(token.actorId)),
                                    List.of(
                                            token.marker,
                                            "1|D|" + encode(projection),
                                            Long.toString(ttl),
                                            Long.toString(projection.expiresAt().toEpochMilli()))));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Caller must have confirmed the DB commit before calling this method. False requires DB fallback. */
    public boolean invalidateAfterCommit(UUID actorId) {
        String key = redisKey(actorId);
        try (Jedis jedis = connections.get()) {
            return "OK"
                    .equals(jedis.set(key, marker(), SetParams.setParams().px(MARKER_TTL_MILLIS)));
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static String redisKey(UUID actorId) {
        if (actorId == null || actorId.equals(new UUID(0, 0)))
            throw new IllegalArgumentException("actorId must be a nonzero UUID");
        return "tc:user:active-hold:" + actorId;
    }

    private static Lookup miss(FillToken token) {
        return new Lookup(Optional.empty(), Optional.ofNullable(token));
    }

    private static String marker() {
        return "1|M|" + UUID.randomUUID();
    }

    private static boolean validMarker(String value) {
        if (value == null
                || !value.matches(
                        "1\\|M\\|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            return false;
        return !UUID.fromString(value.substring(4)).equals(new UUID(0, 0));
    }

    private static String encode(ActiveHoldProjection projection) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                output.writeByte(2);
                writeUuid(output, projection.id());
                writeUuid(output, projection.buyerId());
                writeUuid(output, projection.eventId());
                output.writeLong(projection.expiresAt().getEpochSecond());
                output.writeInt(projection.expiresAt().getNano());
                output.writeByte(projection.items().size());
                for (var item : projection.items()) {
                    writeUuid(output, item.zoneId());
                    output.writeBoolean(item.seatId() != null);
                    if (item.seatId() != null) writeUuid(output, item.seatId());
                    output.writeByte(item.quantity());
                    output.writeUTF(item.unitPrice().toPlainString());
                }
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("memory output failed", impossible);
        }
    }

    private static Optional<ActiveHoldProjection> decode(String payload) {
        if (payload.length() > MAX_PAYLOAD_CHARS) return Optional.empty();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(payload);
            if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(payload))
                return Optional.empty();
            try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (input.readUnsignedByte() != 2) return Optional.empty();
                UUID id = readUuid(input);
                UUID buyer = readUuid(input);
                UUID event = readUuid(input);
                long seconds = input.readLong();
                int nanos = input.readInt();
                if (nanos < 0 || nanos > 999_999_999) return Optional.empty();
                Instant expires = Instant.ofEpochSecond(seconds, nanos);
                int count = input.readUnsignedByte();
                if (count < 1 || count > 8) return Optional.empty();
                var items = new ArrayList<ActiveHoldProjection.Item>();
                for (int index = 0; index < count; index++) {
                    UUID zone = readUuid(input);
                    int hasSeat = input.readUnsignedByte();
                    if (hasSeat > 1) return Optional.empty();
                    UUID seat = hasSeat == 1 ? readUuid(input) : null;
                    int quantity = input.readUnsignedByte();
                    String price = input.readUTF();
                    if (!price.matches("0|[1-9][0-9]{0,18}")) return Optional.empty();
                    items.add(
                            new ActiveHoldProjection.Item(
                                    zone, seat, quantity, new BigDecimal(price)));
                }
                if (input.available() != 0) return Optional.empty();
                return Optional.of(new ActiveHoldProjection(id, buyer, event, expires, items));
            }
        } catch (IOException | RuntimeException corrupt) {
            return Optional.empty();
        }
    }

    private static void writeUuid(DataOutputStream output, UUID id) throws IOException {
        output.writeLong(id.getMostSignificantBits());
        output.writeLong(id.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
