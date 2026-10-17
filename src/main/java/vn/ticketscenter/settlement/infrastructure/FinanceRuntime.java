package vn.ticketscenter.settlement.infrastructure;

import jakarta.servlet.ServletContext;
import java.sql.DriverManager;
import javax.sql.DataSource;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;

/** Lazy connection factory: pooled via PersistenceRegistry, with fallback to DriverManager. */
public final class FinanceRuntime {
    private FinanceRuntime() {}

    public static FinanceDatabase database(ServletContext context) {
        synchronized (context) {
            var configured = context.getAttribute(FinanceDatabase.class.getName());
            if (configured instanceof FinanceDatabase database) return database;

            Object reg = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            FinanceDatabase database;
            if (reg instanceof PersistenceRegistry registry && registry.hasPrincipal(DatabasePrincipal.ADMIN)) {
                DataSource ds = registry.dataSource(DatabasePrincipal.ADMIN);
                database = ds::getConnection;
            } else {
                database = () -> DriverManager.getConnection(
                        DatabaseConfig.getJdbcUrl(),
                        DatabaseConfig.getPrincipalUser(DatabasePrincipal.ADMIN),
                        DatabaseConfig.getPrincipalPassword(DatabasePrincipal.ADMIN));
            }
            context.setAttribute(FinanceDatabase.class.getName(), database);
            return database;
        }
    }
}
