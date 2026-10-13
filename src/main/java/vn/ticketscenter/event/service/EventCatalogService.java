package vn.ticketscenter.event.service;

import vn.ticketscenter.event.cache.EventCatalogCache;
import vn.ticketscenter.event.dto.EventDtos;
import vn.ticketscenter.event.dto.EventFilter;
import vn.ticketscenter.event.repository.EventCatalogRepository;
import java.util.UUID;
import java.util.function.Supplier;

public final class EventCatalogService {
    public static final int AVAILABILITY_TTL = 30;
    public static final int DETAIL_TTL = 120;
    public static final int CATEGORIES_TTL = 300;
    private final EventCatalogRepository repository;
    private final EventCatalogCache cache;
    public EventCatalogService(EventCatalogRepository repository, EventCatalogCache cache) {
        this.repository = repository; this.cache = cache;
    }
    public String events(EventFilter filter) {
        return cached(filter.cacheKey(), AVAILABILITY_TTL, () -> repository.events(filter).toJson(filter));
    }
    public String event(UUID id) {
        return cached("tc:event:detail:v1:" + id, DETAIL_TTL,
                () -> EventDtos.data(repository.event(id).orElseThrow(NotFound::new).toJson()));
    }
    public String zones(UUID id) {
        return cached("tc:event:zones:v1:" + id, AVAILABILITY_TTL,
                () -> EventDtos.data(EventDtos.array(repository.zones(id).orElseThrow(NotFound::new), EventDtos.Zone::toJson)));
    }
    public String seats(UUID id) {
        return cached("tc:zone:seats:v1:" + id, AVAILABILITY_TTL,
                () -> EventDtos.data(EventDtos.array(repository.seats(id).orElseThrow(NotFound::new), EventDtos.Seat::toJson)));
    }
    public String categories() {
        return cached("tc:event:categories:v1", CATEGORIES_TTL,
                () -> EventDtos.data(EventDtos.array(repository.categories(), EventDtos.Category::toJson)));
    }
    public String image(UUID id) {
        String url=repository.event(id).orElseThrow(NotFound::new).coverImageUrl();
        if(url==null || url.isBlank()) throw new NotFound();return url;
    }
    private String cached(String key, int ttl, Supplier<String> load) {
        // A prior demo projection or changed synthetic label must never bypass the real-data SQL boundary.
        if (!vn.ticketscenter.config.ServerConfig.isDemoData()) return load.get();
        try { var hit = cache.get(key); if (hit.isPresent()) return hit.get(); }
        catch (RuntimeException ignored) { }
        String generation;
        try { generation=cache.generation(); } catch(RuntimeException e) { generation=null; }
        String json = load.get(); // DB transaction finishes before a projection is cached. Errors are never cached.
        try { cache.putIfGeneration(key, json, ttl,generation); } catch (RuntimeException ignored) { }
        return json;
    }
    public static final class NotFound extends RuntimeException { }
}
