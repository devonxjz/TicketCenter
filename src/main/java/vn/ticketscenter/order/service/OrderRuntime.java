package vn.ticketscenter.order.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.SingleConnectionPools;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.order.cache.RedisOrderCache;
import vn.ticketscenter.order.dao.OrderRepository;

import java.time.Clock;

@WebListener
public final class OrderRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = OrderService.class.getName();
    private static final String POOL_ATTRIBUTE = OrderRuntime.class.getName() + ".pool";
    private static final String CLOSED_ATTRIBUTE = OrderRuntime.class.getName() + ".closed";

    public static OrderService resolve(ServletContext context) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(CLOSED_ATTRIBUTE)))
                throw OrderException.unavailable();
            if (context.getAttribute(SERVICE_ATTRIBUTE) instanceof OrderService service)
                return service;
            HikariDataSource pool = null;
            try {
                String user = AppConfig.get("db.buyer.user"),
                        password = AppConfig.get("db.buyer.password");
                if (user == null || user.isBlank() || password == null || password.isBlank())
                    throw OrderException.unavailable();
                pool = SingleConnectionPools.open("order-buyer", user, password);
                var redis = RedisClientProvider.getInstance();
                OrderService service =
                        new OrderService(
                                new OrderRepository(ConnectionBudgets.wrap(pool)),
                                new RedisOrderCache(redis::getResource, Clock.systemUTC()));
                context.setAttribute(POOL_ATTRIBUTE, pool);
                context.setAttribute(SERVICE_ATTRIBUTE, service);
                return service;
            } catch (RuntimeException failure) {
                if (pool != null) pool.close();
                throw OrderException.unavailable();
            }
        }
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, false);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        synchronized (event.getServletContext()) {
            event.getServletContext().setAttribute(CLOSED_ATTRIBUTE, true);
            if (event.getServletContext().getAttribute(POOL_ATTRIBUTE)
                    instanceof HikariDataSource pool) pool.close();
            event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
            event.getServletContext().removeAttribute(POOL_ATTRIBUTE);
        }
    }
}
