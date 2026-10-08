package vn.ticketscenter.identity.security;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.ServerConfig;

import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.util.logging.Logger;

@WebListener
public final class AuthSecurityListener implements ServletContextListener {

    private static final Logger LOGGER = Logger.getLogger(AuthSecurityListener.class.getName());
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

        // Khởi tạo trước AuthService nếu PersistenceRegistry đã sẵn sàng
        resolveAuthService(context);
    }

    /**
     * Lấy hoặc khởi tạo duy nhất một AuthService singleton gắn với ServletContext.
     */
    public static AuthService resolveAuthService(ServletContext context) {
        if (context == null) return null;
        Object existing = context.getAttribute(AUTH_SERVICE_ATTRIBUTE);
        if (existing instanceof AuthService authService) {
            return authService;
        }
        synchronized (context) {
            existing = context.getAttribute(AUTH_SERVICE_ATTRIBUTE);
            if (existing instanceof AuthService authService) {
                return authService;
            }
            Object regObj = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (regObj instanceof PersistenceRegistry registry && registry.isReady()) {
                AuthService service = new AuthService(
                        registry.transactionManager(),
                        new UserRepository(),
                        new PasswordHasher(),
                        RedisClientProvider.getInstance()
                );
                context.setAttribute(AUTH_SERVICE_ATTRIBUTE, service);
                return service;
            }
        }
        return null;
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {}
}
