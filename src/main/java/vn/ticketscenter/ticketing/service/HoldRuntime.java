package vn.ticketscenter.ticketing.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.ticketing.cache.RedisActiveHoldCache;
import vn.ticketscenter.ticketing.cache.RedisHoldIdempotency;
import vn.ticketscenter.ticketing.dao.HoldRepository;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.WORKER;

@WebListener
public final class HoldRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = HoldService.class.getName();
    private static final Logger LOG = Logger.getLogger(HoldRuntime.class.getName());
    private ScheduledExecutorService expiry;

    public static HoldService resolve(ServletContext context) {
        return FeatureContext.resolve(
                context, SERVICE_ATTRIBUTE, HoldRuntime::create, failure -> HoldException.unavailable());
    }

    private static HoldService create(FeatureContext.Resources resources) {
        HikariDataSource buyer = resources.strictPool("hold-buyer", BUYER);
        HikariDataSource worker = resources.strictPool("hold-worker", WORKER);
        RedisClientProvider redis = RedisClientProvider.getInstance();
        return new HoldServiceImpl(
                new HoldRepository(ConnectionBudgets.wrap(buyer), ConnectionBudgets.wrap(worker)),
                new RedisActiveHoldCache(redis::getResource, Clock.systemUTC()),
                new RedisHoldIdempotency(redis::getResource));
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        FeatureContext.open(context, SERVICE_ATTRIBUTE);
        expiry =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon(true).name("hold-expiry").factory());
        expiry.scheduleWithFixedDelay(() -> expire(context), 0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (expiry != null) {
            expiry.shutdownNow();
        }
        FeatureContext.close(event.getServletContext(), SERVICE_ATTRIBUTE);
    }

    private static void expire(ServletContext context) {
        try {
            resolve(context).expire(50);
        } catch (RuntimeException failure) {
            LOG.warning("Hold expiry will retry");
        }
    }
}
