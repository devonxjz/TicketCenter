package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestRepository;
import vn.ticketscenter.fulfillment.refund.service.RedisRefundRequestCache;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.fulfillment.refund.service.RefundRequestService;

import java.time.Clock;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

public final class RefundRuntime {
    public static final String SERVICE_ATTRIBUTE = RefundRequestService.class.getName();

    private RefundRuntime() {}

    public static RefundRequestService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    private static RefundRequestService create(ServletContext context) {
        var registry =
                FeatureContext.registry(
                        context,
                        BUYER,
                        () ->
                                new RefundException(
                                        503, "DATABASE_UNAVAILABLE", "Hệ thống hoàn vé chưa sẵn sàng."));
        return new RefundRequestService(
                new RefundRequestRepository(registry.transactionManager()),
                new RedisRefundRequestCache(),
                Clock.systemUTC());
    }
}
