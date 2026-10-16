package vn.ticketscenter.fulfillment.checkin.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.ScanParams;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository.EventInfo;

import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Redis is optional. Cache data is bounded and parsed as JSON, never Java deserialization. */
public final class RedisCheckInEventCache implements CheckInEventCache {
    public static final int EVENTS_TTL_SECONDS = 60;
    public static final int WINDOW_TTL_SECONDS = 30;
    private final Supplier<Jedis> connections;
    public RedisCheckInEventCache() { this(() -> RedisClientProvider.getInstance().getResource()); }
    public RedisCheckInEventCache(Supplier<Jedis> connections) { this.connections = connections; }

    public static String eventsKey(UUID actorId, UUID organizationId) {
        return "tc:checkin:events:v1:" + actorId + ":" + organizationId;
    }
    public static String windowKey(UUID eventId) { return "tc:checkin:window:v1:" + eventId; }

    @Override public Optional<List<EventInfo>> events(UUID actorId, UUID organizationId) {
        try (Jedis redis = connections.get()) {
            String value = redis.get(eventsKey(actorId, organizationId));
            if (value == null || value.length() > 2_000_000) return Optional.empty();
            if (value.isEmpty()) return Optional.of(List.of());
            return Optional.of(Arrays.stream(value.split("\n")).map(RedisCheckInEventCache::decode).toList());
        } catch (RuntimeException exception) { return Optional.empty(); }
    }
    @Override public void putEvents(UUID actorId, UUID organizationId, List<EventInfo> events) {
        String value = events.stream().map(RedisCheckInEventCache::encode).collect(Collectors.joining("\n"));
        if (value.length() > 2_000_000) return;
        try (Jedis redis = connections.get()) {
            redis.setex(eventsKey(actorId, organizationId), EVENTS_TTL_SECONDS, value);
        } catch (RuntimeException ignored) { /* Database remains the source of truth. */ }
    }
    @Override public Optional<EventInfo> window(UUID eventId) {
        try (Jedis redis = connections.get()) {
            String value = redis.get(windowKey(eventId));
            return value == null || value.length() > 16_384 ? Optional.empty() : Optional.of(decode(value));
        } catch (RuntimeException exception) { return Optional.empty(); }
    }
    @Override public void putWindow(EventInfo event) {
        try (Jedis redis = connections.get()) {
            redis.setex(windowKey(event.eventId()), WINDOW_TTL_SECONDS, encode(event));
        } catch (RuntimeException ignored) { }
    }
    @Override public void invalidate(UUID eventId, UUID organizationId) {
        try (Jedis redis = connections.get()) {
            redis.del(windowKey(eventId));
            String cursor = "0";
            ScanParams params = new ScanParams().match("tc:checkin:events:v1:*:" + organizationId).count(100);
            do {
                var result = redis.scan(cursor, params);
                if (!result.getResult().isEmpty()) redis.del(result.getResult().toArray(String[]::new));
                cursor = result.getCursor();
            } while (!"0".equals(cursor));
        } catch (RuntimeException ignored) { }
    }
    private static String encode(EventInfo e) {
        return "{\"eventId\":\"" + e.eventId() + "\",\"organizationId\":\"" + e.organizationId()
                + "\",\"title\":" + HttpResponses.jsonString(e.title()) + ",\"venue\":" + HttpResponses.jsonString(e.venue())
                + ",\"startTime\":\"" + e.startTime() + "\",\"endTime\":\"" + e.endTime()
                + "\",\"status\":" + HttpResponses.jsonString(e.status())
                + ",\"issued\":\"" + e.issued() + "\",\"checkedIn\":\"" + e.checkedIn() + "\"}";
    }
    private static EventInfo decode(String value) {
        try {
            var e = JsonObjectParser.parse(new StringReader(value));
            return new EventInfo(UUID.fromString(e.get("eventId")), UUID.fromString(e.get("organizationId")),
                    Objects.requireNonNull(e.get("title")), Objects.requireNonNull(e.get("venue")),
                    Instant.parse(e.get("startTime")), Instant.parse(e.get("endTime")), Objects.requireNonNull(e.get("status")),
                    Long.parseLong(e.get("issued")), Long.parseLong(e.get("checkedIn")));
        } catch (Exception exception) { throw new IllegalArgumentException("Invalid check-in cache entry"); }
    }
}
