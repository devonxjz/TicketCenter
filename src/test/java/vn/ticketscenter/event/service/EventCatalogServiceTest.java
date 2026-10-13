package vn.ticketscenter.event.service;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.cache.EventCatalogCache;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.repository.EventCatalogRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventCatalogServiceTest {
    private String previousMode, previousEnv;
    @org.junit.jupiter.api.BeforeEach void fixtureMode() {
        previousMode = System.getProperty("app.data.mode"); previousEnv = System.getProperty("app.env");
        System.setProperty("app.data.mode", "demo"); System.setProperty("app.env", "test");
    }
    @org.junit.jupiter.api.AfterEach void restoreMode() {
        if (previousMode == null) System.clearProperty("app.data.mode"); else System.setProperty("app.data.mode", previousMode);
        if (previousEnv == null) System.clearProperty("app.env"); else System.setProperty("app.env", previousEnv);
    }
    private final UUID id = UUID.randomUUID();
    private static class Cache implements EventCatalogCache {
        final Map<String, String> values = new HashMap<>();
        final Map<String, Integer> ttls = new HashMap<>();
        boolean broken;
        public Optional<String> get(String key) { if (broken) throw new IllegalStateException("Redis offline"); return Optional.ofNullable(values.get(key)); }
        public void put(String key, String json, int ttl) { if (broken) throw new IllegalStateException("Redis offline"); values.put(key, json); ttls.put(key, ttl); }
    }
    private class Repository extends EventCatalogRepository {
        int calls;
        boolean broken, hidden;
        Repository() { super(null); }
        private void read() { calls++; if (broken) throw new IllegalStateException("private SQL detail"); }
        @Override public EventDtos.Page events(EventFilter filter) { read(); return new EventDtos.Page(hidden ? List.of() : List.of(eventValue()), hidden ? 0 : 1); }
        @Override public Optional<EventDtos.Event> event(UUID value) { read(); return hidden ? Optional.empty() : Optional.of(eventValue()); }
        @Override public Optional<List<EventDtos.Zone>> zones(UUID value) { read(); return hidden ? Optional.empty() : Optional.of(List.of()); }
        @Override public Optional<List<EventDtos.Seat>> seats(UUID value) { read(); return hidden ? Optional.empty() : Optional.of(List.of()); }
        @Override public List<EventDtos.Category> categories() { read(); return List.of(new EventDtos.Category(id, "Âm nhạc", "music")); }
    }
    private EventDtos.Event eventValue() {
        var time = Instant.parse("2026-10-10T01:00:00Z");
        return new EventDtos.Event(id, id, "Nhạc \"live\"\n<script>", "Mô tả", null, "Nhà hát", "TP.HCM", time, time, time, time,
                new BigDecimal("300000"), "Âm nhạc", "Ban tổ chức", new BigDecimal("500000"), false);
    }
    @Test void cacheHitAvoidsSecondRepositoryReadAndFiltersUseDistinctKeys() {
        var repository = new Repository(); var cache = new Cache(); var service = new EventCatalogService(repository, cache);
        var filter = EventFilter.parse(Map.of());
        String first = service.events(filter);
        assertEquals(first, service.events(filter)); assertEquals(1, repository.calls);
        service.events(EventFilter.parse(Map.of("page", "2"))); assertEquals(2, repository.calls);
        assertTrue(first.contains("\"page\":1,\"pageSize\":6,\"total\":1"));
        assertEquals(30, cache.ttls.get(filter.cacheKey()));
    }
    @Test void redisFailureUsesDatabaseAndDatabaseFailureIsNeverCached() {
        var repository = new Repository(); var cache = new Cache(); cache.broken = true;
        var service = new EventCatalogService(repository, cache);
        assertTrue(service.event(id).contains(id.toString()));
        cache.broken = false; repository.broken = true;
        assertThrows(IllegalStateException.class, () -> service.event(id)); assertTrue(cache.values.isEmpty());
    }
    @Test void realModeRejectsPreviouslyCachedSyntheticProjectionAndRechecksEveryRead() {
        var repository = new Repository(); var cache = new Cache(); var service = new EventCatalogService(repository, cache);
        service.event(id); // A fixture projection is already cached.
        System.setProperty("app.data.mode", "real");
        repository.hidden = true;
        assertThrows(EventCatalogService.NotFound.class, () -> service.event(id));
        assertThrows(EventCatalogService.NotFound.class, () -> service.event(id));
        assertEquals(3, repository.calls);
    }
    @Test void missingOrHiddenParentsAre404AndEmptyCollectionsAreSuccess() {
        var repository = new Repository(); var cache = new Cache(); var service = new EventCatalogService(repository, cache);
        repository.hidden = true;
        assertThrows(EventCatalogService.NotFound.class, () -> service.event(id));
        assertThrows(EventCatalogService.NotFound.class, () -> service.zones(id));
        assertThrows(EventCatalogService.NotFound.class, () -> service.seats(id)); assertTrue(cache.values.isEmpty());
        repository.hidden = false;
        assertEquals("{\"data\":[]}", service.zones(id)); assertEquals("{\"data\":[]}", service.seats(id));
    }
    @Test void projectionsEscapeTextAndKeepIntegerMoneyAndUtc() {
        String json = eventValue().toJson();
        assertTrue(json.contains("Nhạc \\\"live\\\"\\n<script>"));
        assertTrue(json.contains("\"minPrice\":\"300000\""));
        assertTrue(json.contains("2026-10-10T01:00:00Z")); assertTrue(json.contains("\"coverImageUrl\":null"));
        assertFalse(json.contains("organizationId"));
    }
    @Test void allProjectionRoutesHaveVersionedKeysAndCategoriesUseFiveMinuteTtl() {
        var repository = new Repository(); var cache = new Cache(); var service = new EventCatalogService(repository, cache);
        service.event(id); service.event(id); service.zones(id); service.zones(id); service.seats(id); service.seats(id);
        service.categories(); service.categories(); assertEquals(4, repository.calls);
        assertEquals(Set.of("tc:event:detail:v1:" + id, "tc:event:zones:v1:" + id,
                "tc:zone:seats:v1:" + id, "tc:event:categories:v1"), cache.values.keySet());
        assertEquals(300, cache.ttls.get("tc:event:categories:v1"));
        assertEquals(120, cache.ttls.get("tc:event:detail:v1:" + id));
        assertTrue(cache.values.get("tc:event:detail:v1:" + id).contains("\"maxPrice\":\"500000\""));
    }
    @Test void zoneAndSeatUseIssueFieldNamesAndIntegerCurrency() {
        String zone = new EventDtos.Zone(id, id, "VIP", "SEATED", new BigDecimal("300000"), 3, 1, 1, 1).toJson();
        assertTrue(zone.contains("\"zoneType\":\"SEATED\"")); assertTrue(zone.contains("\"basePrice\":\"300000\""));
        assertTrue(zone.contains("\"totalCapacity\":3")); assertTrue(zone.contains("\"availableSeats\":1"));
        String seat = new EventDtos.Seat(id, id, "A", 1, "A1", "AVAILABLE").toJson();
        assertTrue(seat.contains("\"seatLabel\":\"A1\""));
    }
}
