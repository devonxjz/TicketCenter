package vn.ticketscenter.order.coupon.service;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.SingleConnectionPools;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.order.coupon.repository.CouponAdminRepository;
import vn.ticketscenter.order.service.OrderException;

@WebListener
public final class CouponAdminRuntime implements ServletContextListener {
    private static final String SERVICE = CouponAdminRuntime.class.getName() + ".service";
    private static final String MANAGER_POOL = CouponAdminRuntime.class.getName() + ".managerPool";
    private static final String ADMIN_POOL = CouponAdminRuntime.class.getName() + ".adminPool";

    public static CouponAdminService resolve(ServletContext context) {
        synchronized (context) {
            if (context.getAttribute(SERVICE) instanceof CouponAdminService service) return service;
            HikariDataSource manager = null;
            HikariDataSource admin = null;
            try {
                manager = pool("manager");
                admin = pool("admin");
                var redis = RedisClientProvider.getInstance();
                var repository =
                        new CouponAdminRepository(
                                ConnectionBudgets.wrap(manager), ConnectionBudgets.wrap(admin));
                var service = new CouponAdminService(repository, redis::getResource);
                context.setAttribute(MANAGER_POOL, manager);
                context.setAttribute(ADMIN_POOL, admin);
                context.setAttribute(SERVICE, service);
                return service;
            } catch (RuntimeException failure) {
                if (manager != null) manager.close();
                if (admin != null) admin.close();
                throw OrderException.unavailable();
            }
        }
    }

    private static HikariDataSource pool(String principal) {
        String user = AppConfig.get("db." + principal + ".user");
        String password = AppConfig.get("db." + principal + ".password");
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            throw OrderException.unavailable();
        }
        return SingleConnectionPools.open("coupon-" + principal, user, password);
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        ServletContext context = event.getServletContext();
        synchronized (context) {
            context.removeAttribute(SERVICE);
            if (context.getAttribute(MANAGER_POOL) instanceof HikariDataSource pool) pool.close();
            if (context.getAttribute(ADMIN_POOL) instanceof HikariDataSource pool) pool.close();
            context.removeAttribute(MANAGER_POOL);
            context.removeAttribute(ADMIN_POOL);
        }
    }
}
