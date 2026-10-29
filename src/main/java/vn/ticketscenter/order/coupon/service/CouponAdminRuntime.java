package vn.ticketscenter.order.coupon.service;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import vn.ticketscenter.config.persistence.ConnectionBudgets;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.order.coupon.dao.CouponAdminRepository;
import vn.ticketscenter.order.service.OrderException;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.ADMIN;
import static vn.ticketscenter.config.persistence.DatabasePrincipal.MANAGER;

@WebListener
public final class CouponAdminRuntime implements ServletContextListener {
    public static final String SERVICE_ATTRIBUTE = CouponAdminService.class.getName();

    public static CouponAdminService resolve(ServletContext context) {
        return FeatureContext.resolve(
                context, SERVICE_ATTRIBUTE, CouponAdminRuntime::create, failure -> OrderException.unavailable());
    }

    private static CouponAdminService create(FeatureContext.Resources resources) {
        var manager = resources.strictPool("coupon-manager", MANAGER);
        var admin = resources.strictPool("coupon-admin", ADMIN);
        var redis = RedisClientProvider.getInstance();
        return new CouponAdminServiceImpl(
                new CouponAdminRepository(ConnectionBudgets.wrap(manager), ConnectionBudgets.wrap(admin)),
                redis::getResource);
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
