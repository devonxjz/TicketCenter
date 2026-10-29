package vn.ticketscenter.report.service;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.report.cache.ReportCache;
import vn.ticketscenter.settlement.service.FinanceRuntime;

/** The finance report service shared through the servlet context. */
public final class ReportRuntime {
    public static final String SERVICE_ATTRIBUTE = ReportService.class.getName();

    private ReportRuntime() {}

    public static ReportService resolve(ServletContext context) {
        return FeatureContext.cached(
                context,
                SERVICE_ATTRIBUTE,
                () ->
                        new ReportServiceImpl(
                                FinanceRuntime.database(context),
                                AppConfig.getBoolean("finance.redis.enabled", true)
                                        ? ReportCache.redis()
                                        : ReportCache.none()));
    }
}
