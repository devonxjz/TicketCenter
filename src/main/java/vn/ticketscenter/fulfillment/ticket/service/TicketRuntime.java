package vn.ticketscenter.fulfillment.ticket.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.SingleConnectionPools;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.ticket.cache.RedisTicketCache;
import vn.ticketscenter.fulfillment.ticket.dao.TicketRepository;

@WebListener
public final class TicketRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = TicketService.class.getName();
    private static final String POOL_ATTRIBUTE = TicketRuntime.class.getName() + ".pool";
    private static final String CLOSED_ATTRIBUTE = TicketRuntime.class.getName() + ".closed";

    public static TicketService resolve(ServletContext context) {
        synchronized (context) {
            if (Boolean.TRUE.equals(context.getAttribute(CLOSED_ATTRIBUTE))) {
                throw TicketException.unavailable();
            }
            if (context.getAttribute(SERVICE_ATTRIBUTE) instanceof TicketService service) {
                return service;
            }

            HikariDataSource pool = null;
            try {
                String user = AppConfig.get("db.buyer.user");
                if (user == null || user.isBlank()) user = DatabaseConfig.getUser();
                String password = AppConfig.get("db.buyer.password");
                if (password == null || password.isBlank()) password = DatabaseConfig.getPassword();

                pool = SingleConnectionPools.open("ticket-buyer", user, password);

                SignedTicketQrSigner signer = SignedTicketQrSigner.configured();
                var redis = RedisClientProvider.getInstance();
                RedisTicketCache cache = new RedisTicketCache(redis::getResource);
                TicketRepository repository = new TicketRepository(ConnectionBudgets.wrap(pool));

                TicketService service = new TicketService(repository, signer, cache);
                context.setAttribute(POOL_ATTRIBUTE, pool);
                context.setAttribute(SERVICE_ATTRIBUTE, service);
                return service;
            } catch (RuntimeException failure) {
                if (pool != null) pool.close();
                throw TicketException.unavailable();
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
                    instanceof HikariDataSource pool) {
                pool.close();
            }
            event.getServletContext().removeAttribute(SERVICE_ATTRIBUTE);
            event.getServletContext().removeAttribute(POOL_ATTRIBUTE);
        }
    }
}
