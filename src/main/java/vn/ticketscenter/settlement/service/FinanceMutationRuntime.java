package vn.ticketscenter.settlement.service;

import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.persistence.FeatureContext;
import vn.ticketscenter.dashboard.cache.DashboardCache;
import vn.ticketscenter.report.cache.ReportCache;
import vn.ticketscenter.settlement.cache.SettlementReadCache;
import vn.ticketscenter.settlement.commission.cache.CommissionRuleCache;
import vn.ticketscenter.settlement.dao.FinanceRepository;
import vn.ticketscenter.settlement.infrastructure.FinanceRuntime;
import vn.ticketscenter.settlement.payout.PayoutAdapter;
import vn.ticketscenter.settlement.payout.PayoutAdapterImpl;

/** The admin commission/settlement/payout service shared through the servlet context. */
public final class FinanceMutationRuntime {
    public static final String SERVICE_ATTRIBUTE = FinanceMutationService.class.getName();

    private FinanceMutationRuntime() {}

    public static FinanceMutationService resolve(ServletContext context) {
        return FeatureContext.cached(context, SERVICE_ATTRIBUTE, () -> create(context));
    }

    private static FinanceMutationService create(ServletContext context) {
        var database = FinanceRuntime.database(context);
        PayoutAdapter adapter = PayoutAdapter.unavailable();
        if ("fixture-simulated".equals(AppConfig.get("payout.adapter", "unavailable"))) {
            adapter =
                    new PayoutAdapterImpl(
                            database,
                            AppConfig.get("payout.simulated.outcome", "SUCCEEDED"),
                            AppConfig.getBoolean("payout.simulated.timeoutAfterSideEffect", false));
        }
        boolean caching = AppConfig.getBoolean("finance.redis.enabled", true);
        var cache = caching ? CommissionRuleCache.redis() : CommissionRuleCache.none();
        var settlementCache = caching ? SettlementReadCache.redis() : SettlementReadCache.none();
        var repository =
                new FinanceRepository(
                        database,
                        org -> {
                            if (caching) {
                                cache.invalidate(org);
                                ReportCache.invalidateAfterCommit(org);
                                DashboardCache.invalidateAdminAfterCommit();
                            }
                        },
                        settlementCache::invalidate);
        return new FinanceMutationServiceImpl(repository, adapter, cache, settlementCache);
    }
}
