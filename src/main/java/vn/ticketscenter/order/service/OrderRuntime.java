package vn.ticketscenter.order.service;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.order.cache.RedisOrderCache;
import vn.ticketscenter.order.dao.OrderRepository;

import java.time.Clock;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

@WebListener
public final class OrderRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = OrderService.class.getName();

    public static OrderService resolve(ServletContext context) {
        return FeatureContext.resolve(
                context, SERVICE_ATTRIBUTE, OrderRuntime::create, failure -> OrderException.unavailable());
    }

    private static OrderService create(FeatureContext.Resources resources) {
        var buyer = resources.strictPool("order-buyer", BUYER);
        var redis = RedisClientProvider.getInstance();
        return new OrderService(
                new OrderRepository(ConnectionBudgets.wrap(buyer)),
                new RedisOrderCache(redis::getResource, Clock.systemUTC()));
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        FeatureContext.open(event.getServletContext(), SERVICE_ATTRIBUTE);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        FeatureContext.close(event.getServletContext(), SERVICE_ATTRIBUTE);
    }
}
