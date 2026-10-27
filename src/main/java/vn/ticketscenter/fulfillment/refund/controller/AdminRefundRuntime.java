package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.fulfillment.refund.dao.AdminRefundRepository;
import vn.ticketscenter.fulfillment.refund.service.AdminRefundDecisionService;
import vn.ticketscenter.fulfillment.refund.service.AdminRefundDecisionServiceImpl;
import vn.ticketscenter.fulfillment.refund.cache.AdminRefundCacheImpl;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.ADMIN;

public final class AdminRefundRuntime {
    public static final String SERVICE_ATTRIBUTE = AdminRefundDecisionService.class.getName();

    private AdminRefundRuntime() {}

    public static AdminRefundDecisionService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    private static AdminRefundDecisionService create(ServletContext context) {
        var registry =
                FeatureContext.registry(
                        context,
                        ADMIN,
                        () ->
                                new RefundException(
                                        503,
                                        "DATABASE_UNAVAILABLE",
                                        "Hệ thống quản trị hoàn tiền chưa sẵn sàng."));
        return new AdminRefundDecisionServiceImpl(
                new AdminRefundRepository(registry.transactionManager()), new AdminRefundCacheImpl());
    }
}
