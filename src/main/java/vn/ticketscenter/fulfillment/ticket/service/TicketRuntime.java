package vn.ticketscenter.fulfillment.ticket.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.ticket.cache.TicketCacheImpl;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

@WebListener
public final class TicketRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = TicketService.class.getName();

    public static TicketService resolve(ServletContext context) {
        return FeatureContext.resolve(
                context, SERVICE_ATTRIBUTE, TicketRuntime::create, failure -> TicketException.unavailable());
    }

    private static TicketService create(FeatureContext.Resources resources) {
        HikariDataSource buyer = resources.fallbackPool("ticket-buyer", BUYER, true, 10_000);
        SignedTicketQrSigner signer = SignedTicketQrSigner.configured();
        RedisClientProvider redis = RedisClientProvider.getInstance();
        return new TicketServiceImpl(
                new TicketRepository(ConnectionBudgets.wrap(buyer)),
                signer,
                new TicketCacheImpl(redis::getResource));
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
