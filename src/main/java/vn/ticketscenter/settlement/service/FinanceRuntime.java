package vn.ticketscenter.settlement.service;

import java.util.Optional;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.FeatureContext;

import java.sql.DriverManager;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.ADMIN;

/** Lazy connection factory: pooled via PersistenceRegistry, with fallback to DriverManager. */
public final class FinanceRuntime {
    private FinanceRuntime() {}

    public static FinanceDatabase database(ServletContext context) {
        return FeatureContext.cached(context, FinanceDatabase.class.getName(), () -> create(context));
    }

    private static FinanceDatabase create(ServletContext context) {
        Optional<PersistenceRegistry> registry = FeatureContext.registry(context).filter(pool -> pool.hasPrincipal(ADMIN));
        if (registry.isPresent()) {
            return registry.get().dataSource(ADMIN)::getConnection;
        }
        return () ->
                DriverManager.getConnection(
                        DatabaseConfig.getJdbcUrl(),
                        DatabaseConfig.getPrincipalUser(ADMIN),
                        DatabaseConfig.getPrincipalPassword(ADMIN));
    }
}
