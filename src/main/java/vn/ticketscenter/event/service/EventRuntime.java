package vn.ticketscenter.event.service;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.event.cache.EventIdempotencyStore;
import vn.ticketscenter.event.dao.EventRepository;
import vn.ticketscenter.storage.StorageServiceImpl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.MANAGER;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.WORKER;

@WebListener
public final class EventRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = EventService.class.getName();
    private static final Logger LOG = Logger.getLogger(EventRuntime.class.getName());
    private ScheduledExecutorService cleanup;

    public static EventService resolve(ServletContext context) {
        return FeatureContext.resolve(context, SERVICE_ATTRIBUTE, EventRuntime::create, failure -> failure);
    }

    private static EventService create(FeatureContext.Resources resources) {
        var manager = resources.fallbackPool("event-images-manager", MANAGER, false, 15_000);
        var buyer = resources.fallbackPool("event-images-buyer", BUYER, false, 15_000);
        var worker = resources.fallbackPool("event-images-worker", WORKER, false, 15_000);
        var repository =
                new EventRepository(
                        ConnectionBudgets.wrap(manager),
                        ConnectionBudgets.wrap(buyer),
                        ConnectionBudgets.wrap(worker));
        return new EventServiceImpl(
                repository, StorageServiceImpl.configured(), EventIdempotencyStore.configured());
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        FeatureContext.open(context, SERVICE_ATTRIBUTE);
        cleanup =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon(true).name("event-image-cleanup").factory());
        cleanup.scheduleWithFixedDelay(() -> cleanImages(context), 60, 60, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (cleanup != null) cleanup.shutdownNow();
        FeatureContext.close(event.getServletContext(), SERVICE_ATTRIBUTE);
    }

    /** Only cleans once a request has built the service; the job never opens pools by itself. */
    private static void cleanImages(ServletContext context) {
        if (!(context.getAttribute(SERVICE_ATTRIBUTE) instanceof EventService service)) return;
        try {
            service.cleanup(10);
        } catch (RuntimeException failure) {
            LOG.warning("Event image cleanup will retry");
        }
    }
}
