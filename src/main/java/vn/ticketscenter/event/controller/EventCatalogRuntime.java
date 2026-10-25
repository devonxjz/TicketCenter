package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.event.cache.RedisEventCatalogCache;
import vn.ticketscenter.event.dao.EventCatalogRepository;
import vn.ticketscenter.event.service.EventCatalogService;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

public final class EventCatalogRuntime {
    public static final String SERVICE_ATTRIBUTE = EventCatalogService.class.getName();
    public static final String CACHE_ATTRIBUTE = RedisEventCatalogCache.class.getName();

    private EventCatalogRuntime() {}

    public static EventCatalogService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    public static RedisEventCatalogCache cache(ServletContext context) {
        return FeatureContext.cached(context, CACHE_ATTRIBUTE, RedisEventCatalogCache::new);
    }

    private static EventCatalogService create(ServletContext context) {
        var registry =
                FeatureContext.registry(
                        context, BUYER, () -> new IllegalStateException("Event catalog unavailable"));
        return new EventCatalogService(
                new EventCatalogRepository(registry.transactionManager()), cache(context));
    }
}
