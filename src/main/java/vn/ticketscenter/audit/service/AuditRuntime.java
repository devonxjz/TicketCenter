package vn.ticketscenter.audit.service;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.settlement.service.FinanceRuntime;

/** The audit read service shared through the servlet context. */
public final class AuditRuntime {
    public static final String SERVICE_ATTRIBUTE = AuditService.class.getName();

    private AuditRuntime() {}

    public static AuditService resolve(ServletContext context) {
        return FeatureContext.cached(
                context,
                SERVICE_ATTRIBUTE,
                () -> new AuditServiceImpl(FinanceRuntime.database(context)));
    }
}
