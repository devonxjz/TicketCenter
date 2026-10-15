package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.refund.repository.AdminRefundRepository;
import vn.ticketscenter.fulfillment.refund.service.*;

public final class AdminRefundRuntime {
    public static final String SERVICE_ATTRIBUTE = AdminRefundDecisionService.class.getName();

    private AdminRefundRuntime() {}

    public static AdminRefundDecisionService resolve(ServletContext context) {
        synchronized (context) {
            var value = context.getAttribute(SERVICE_ATTRIBUTE);
            if (value instanceof AdminRefundDecisionService service) return service;
            var configured = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(configured instanceof PersistenceRegistry registry)
                    || !registry.hasPrincipal(DatabasePrincipal.ADMIN))
                throw new RefundException(
                        503, "DATABASE_UNAVAILABLE", "Hệ thống quản trị hoàn tiền chưa sẵn sàng.");
            var service =
                    new AdminRefundDecisionService(
                            new AdminRefundRepository(registry.transactionManager()),
                            new RedisAdminRefundCache());
            context.setAttribute(SERVICE_ATTRIBUTE, service);
            return service;
        }
    }
}
