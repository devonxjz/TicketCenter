package vn.ticketscenter.dashboard.service;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.dashboard.cache.DashboardCache;
import vn.ticketscenter.settlement.infrastructure.FinanceRuntime;

/** The finance overview service shared through the servlet context. */
public final class DashboardRuntime {
    public static final String SERVICE_ATTRIBUTE = DashboardService.class.getName();

    private DashboardRuntime() {}

    public static DashboardService resolve(ServletContext context) {
        return FeatureContext.cached(
                context,
                SERVICE_ATTRIBUTE,
                () ->
                        new DashboardServiceImpl(
                                FinanceRuntime.database(context), DashboardCache.configured()));
    }
}
