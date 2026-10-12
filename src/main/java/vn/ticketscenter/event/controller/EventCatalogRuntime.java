package vn.ticketscenter.event.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.event.cache.RedisEventCatalogCache;
import vn.ticketscenter.event.repository.EventCatalogRepository;
import vn.ticketscenter.event.service.EventCatalogService;

public final class EventCatalogRuntime {
    public static final String SERVICE_ATTRIBUTE = EventCatalogService.class.getName();
    public static final String CACHE_ATTRIBUTE = RedisEventCatalogCache.class.getName();
    private EventCatalogRuntime() { }
    public static EventCatalogService resolve(ServletContext context) {
        synchronized (context) {
            Object existing = context.getAttribute(SERVICE_ATTRIBUTE);
            if (existing instanceof EventCatalogService service) return service;
            Object value = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(value instanceof PersistenceRegistry registry) || !registry.hasPrincipal(DatabasePrincipal.BUYER))
                throw new IllegalStateException("Event catalog unavailable");
            var service = new EventCatalogService(new EventCatalogRepository(registry.transactionManager()), cache(context));
            context.setAttribute(SERVICE_ATTRIBUTE, service);
            return service;
        }
    }
    public static RedisEventCatalogCache cache(ServletContext context) {
        synchronized(context) {
            Object existing=context.getAttribute(CACHE_ATTRIBUTE);
            if(existing instanceof RedisEventCatalogCache cache) return cache;
            var cache=new RedisEventCatalogCache();context.setAttribute(CACHE_ATTRIBUTE,cache);return cache;
        }
    }
}
