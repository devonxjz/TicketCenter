package vn.ticketscenter.event.service;

import vn.ticketscenter.event.cache.EventCatalogCache;
import vn.ticketscenter.event.dto.EventDtos;
import vn.ticketscenter.event.dto.EventFilter;
import vn.ticketscenter.event.dao.EventCatalogRepository;
import java.util.UUID;
import java.util.function.Supplier;

public final class EventCatalogService {
    public static final int AVAILABILITY_TTL = 30;
    public static final int DETAIL_TTL = 120;
    public static final int CATEGORIES_TTL = 300;
    private final EventCatalogRepository repository;
    private final EventCatalogCache cache;

    public EventCatalogService(EventCatalogRepository repository, EventCatalogCache cache) {
        this.repository = repository;
        this.cache = cache;
    }

    public String events(EventFilter filter) {
        return cached(
                filter.cacheKey(),
                AVAILABILITY_TTL,
                () -> repository.events(filter).toJson(filter));
    }

    public String event(UUID id) {
        return cached(
                "tc:event:detail:v1:" + id,
                DETAIL_TTL,
                () -> EventDtos.data(repository.event(id).orElseThrow(NotFound::new).toJson()));
    }

    public String zones(UUID id) {
        return cached(
                "tc:event:zones:v1:" + id,
                AVAILABILITY_TTL,
                () ->
                        EventDtos.data(
                                EventDtos.array(
                                        repository.zones(id).orElseThrow(NotFound::new),
                                        EventDtos.Zone::toJson)));
    }

    public String seats(UUID id) {
        return cached(
                "tc:zone:seats:v1:" + id,
                AVAILABILITY_TTL,
                () ->
                        EventDtos.data(
                                EventDtos.array(
                                        repository.seats(id).orElseThrow(NotFound::new),
                                        EventDtos.Seat::toJson)));
    }

    public String categories() {
        return cached(
                "tc:event:categories:v1",
                CATEGORIES_TTL,
                () ->
                        EventDtos.data(
                                EventDtos.array(
                                        repository.categories(), EventDtos.Category::toJson)));
    }

    public String image(UUID id) {
        String url = repository.event(id).orElseThrow(NotFound::new).coverImageUrl();
        if (url == null || url.isBlank()) throw new NotFound();
        return url;
    }

    public void warmAsync() {
        if (isCacheEnabled())
            Thread.ofPlatform().daemon(true).name("event-catalog-warmup").start(this::warm);
    }

    public int warm() {
        if (!isCacheEnabled()) return 0;
        try {
            categories();
            var filter = EventFilter.parse(java.util.Map.of());
            events(filter);
            for (var ev : repository.events(filter).events()) {
                event(ev.eventId());
                zones(ev.eventId());
            }
            return 2 + repository.events(filter).events().size() * 2;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String cached(String key, int ttl, Supplier<String> load) {
        if (!isCacheEnabled()) return load.get();
        try {
            var hit = cache.get(key);
            if (hit.isPresent()) return hit.get();
        } catch (RuntimeException ignored) {
        }
        String generation;
        try {
            generation = cache.generation();
        } catch (RuntimeException e) {
            generation = null;
        }
        // DB transaction finishes before a projection is cached. Errors are never cached.
        String json = load.get();
        try {
            cache.putIfGeneration(key, json, ttl, generation);
        } catch (RuntimeException ignored) {
        }
        return json;
    }

    private boolean isCacheEnabled() {
        String explicit =
                vn.ticketscenter.config.AppConfig.get(
                        vn.ticketscenter.config.AppConfig.Keys.REDIS_CACHE_CATALOG_ENABLED);
        if (explicit != null && !explicit.isBlank()) {
            return Boolean.parseBoolean(explicit.trim());
        }
        if (vn.ticketscenter.config.ServerConfig.isProduction()) {
            return true;
        }
        return vn.ticketscenter.config.ServerConfig.isDemoData();
    }

    public static final class NotFound extends vn.ticketscenter.config.error.AppException {
        public NotFound() {
            super(404, "NOT_FOUND", "Not found");
        }
    }
}
