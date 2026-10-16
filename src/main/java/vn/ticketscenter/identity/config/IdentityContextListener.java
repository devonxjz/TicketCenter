package vn.ticketscenter.identity.config;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.ServerConfig;

import java.util.logging.Logger;

/** Hardens the session cookie at start-up and warms {@link IdentityServices}. */
@WebListener
public final class IdentityContextListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(IdentityContextListener.class.getName());

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        ServletContext context = sce.getServletContext();
        SessionCookieConfig config = context.getSessionCookieConfig();
        config.setHttpOnly(true);
        config.setPath(context.getContextPath().isEmpty() ? "/" : context.getContextPath());

        boolean isProduction = "production".equalsIgnoreCase(ServerConfig.getEnv());
        if (isProduction) {
            config.setSecure(true);
        }
        LOGGER.info(
                "IdentityContextListener initialized: SessionCookieConfig HttpOnly=true, Secure="
                        + isProduction);

        // Build the services now when persistence is already up (a no-op otherwise: the first
        // request builds them).
        IdentityServices.from(context);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to release: the services only hold shared pools owned by the persistence
        // listener.
    }
}
