package vn.ticketscenter.ticketing.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.ticketing.cache.RedisActiveHoldCache;
import vn.ticketscenter.ticketing.cache.RedisHoldIdempotency;
import vn.ticketscenter.ticketing.dao.HoldRepository;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

@WebListener
public final class HoldRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = HoldService.class.getName();
    private static final String POOLS_ATTRIBUTE = HoldRuntime.class.getName() + ".pools";
    private static final String CLOSED_ATTRIBUTE = HoldRuntime.class.getName() + ".closed";
    private static final Logger LOG = Logger.getLogger(HoldRuntime.class.getName());
    private ScheduledExecutorService expiry;

    public static HoldService resolve(ServletContext context) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(CLOSED_ATTRIBUTE)))
                throw HoldException.unavailable();
            Object value = context.getAttribute(SERVICE_ATTRIBUTE);
            if (value instanceof HoldService service) return service;
            List<HikariDataSource> pools = new ArrayList<>();
            try {
                HikariDataSource buyer = pool(DatabasePrincipal.BUYER);
                pools.add(buyer);
                HikariDataSource worker = pool(DatabasePrincipal.WORKER);
                pools.add(worker);
                var redis = RedisClientProvider.getInstance();
                HoldService service =
                        new HoldService(
                                new HoldRepository(
                                        ConnectionBudgets.wrap(buyer),
                                        ConnectionBudgets.wrap(worker)),
                                new RedisActiveHoldCache(redis::getResource, Clock.systemUTC()),
                                new RedisHoldIdempotency(redis::getResource));
                context.setAttribute(POOLS_ATTRIBUTE, pools);
                context.setAttribute(SERVICE_ATTRIBUTE, service);
                return service;
            } catch (RuntimeException failure) {
                pools.forEach(HikariDataSource::close);
                throw HoldException.unavailable();
            }
        }
    }

    private static HikariDataSource pool(DatabasePrincipal principal) {
        String prefix = "db." + principal.name().toLowerCase(Locale.ROOT);
        String user = AppConfig.get(prefix + ".user");
        String password = AppConfig.get(prefix + ".password");
        if (user == null || user.isBlank() || password == null || password.isBlank())
            throw HoldException.unavailable();
        HikariConfig config = new HikariConfig();
        config.setPoolName("hold-" + principal.name().toLowerCase(Locale.ROOT));
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(10_000);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, false);
        expiry =
                Executors.newSingleThreadScheduledExecutor(
                        Thread.ofPlatform().daemon(true).name("hold-expiry").factory());
        expiry.scheduleWithFixedDelay(
                () -> {
                    try {
                        resolve(event.getServletContext()).expire(50);
                    } catch (RuntimeException failure) {
                        LOG.warning("Hold expiry will retry");
                    }
                },
                0,
                5,
                TimeUnit.SECONDS);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (expiry != null) expiry.shutdownNow();
        synchronized (event.getServletContext()) {
            event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, true);
            Object value = event.getServletContext().getAttribute(POOLS_ATTRIBUTE);
            if (value instanceof List<?> pools)
                for (Object pool : pools)
                    if (pool instanceof HikariDataSource source) source.close();
            event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
            event.getServletContext().removeAttribute(POOLS_ATTRIBUTE);
        }
    }
}
