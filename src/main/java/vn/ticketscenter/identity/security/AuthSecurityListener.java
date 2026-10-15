package vn.ticketscenter.identity.security;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.service.AuthService;

import java.util.logging.Logger;

/** Configures the session cookie and warms the identity services once persistence is ready. */
@WebListener
public final class AuthSecurityListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AuthSecurityListener.class.getName());

    /** Context attribute that lets a caller (typically a test) replace the {@link AuthService}. */
    public static final String AUTH_SERVICE_ATTRIBUTE = "vn.ticketscenter.identity.AUTH_SERVICE";

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
        LOGGER.info("AuthSecurityListener initialized: SessionCookieConfig HttpOnly=true, Secure=" + isProduction);

        IdentityServices.from(context);
    }

    /**
     * @deprecated use {@link IdentityServices#from(ServletContext)}; kept so callers outside the identity
     *     package keep working until they are migrated.
     */
    @Deprecated
    public static AuthService resolveAuthService(ServletContext context) {
        if (context == null) {
            return null;
        }
        if (context.getAttribute(AUTH_SERVICE_ATTRIBUTE) instanceof AuthService override) {
            return override;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.authService();
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Nothing to release: the services only hold shared pools owned by the persistence listener.
    }
}
