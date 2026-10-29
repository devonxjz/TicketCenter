package vn.ticketscenter.fulfillment.refund.service;

import vn.ticketscenter.config.persistence.PersistenceRegistry;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestRepository;
import vn.ticketscenter.fulfillment.refund.cache.RefundRequestCacheImpl;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.fulfillment.refund.service.RefundRequestService;
import vn.ticketscenter.fulfillment.refund.service.RefundRequestServiceImpl;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.BUYER;

public final class RefundRuntime {
    public static final String SERVICE_ATTRIBUTE = RefundRequestService.class.getName();

    private RefundRuntime() {}

    public static RefundRequestService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    private static RefundRequestService create(ServletContext context) {
        PersistenceRegistry registry =
                FeatureContext.registry(
                        context,
                        BUYER,
                        () ->
                                new RefundException(
                                        503, "DATABASE_UNAVAILABLE", "Hệ thống hoàn vé chưa sẵn sàng."));
        return new RefundRequestServiceImpl(
                new RefundRequestRepository(registry.transactionManager()),
                new RefundRequestCacheImpl());
    }
}
