package vn.ticketscenter.event.service;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.event.cache.EventCatalogCacheImpl;
import vn.ticketscenter.event.dao.EventCatalogRepository;
import vn.ticketscenter.event.service.EventCatalogService;
import vn.ticketscenter.event.service.EventCatalogServiceImpl;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

public final class EventCatalogRuntime {
    public static final String SERVICE_ATTRIBUTE = EventCatalogService.class.getName();
    public static final String CACHE_ATTRIBUTE = EventCatalogCacheImpl.class.getName();

    private EventCatalogRuntime() {}

    public static EventCatalogService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    public static EventCatalogCacheImpl cache(ServletContext context) {
        return FeatureContext.cached(context, CACHE_ATTRIBUTE, EventCatalogCacheImpl::new);
    }

    private static EventCatalogService create(ServletContext context) {
        var registry =
                FeatureContext.registry(
                        context, BUYER, () -> new IllegalStateException("Event catalog unavailable"));
        return new EventCatalogServiceImpl(
                new EventCatalogRepository(registry.transactionManager()), cache(context));
    }
}
