package vn.ticketscenter.settlement.infrastructure;

import jakarta.servlet.ServletContext;
import java.sql.DriverManager;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.DatabasePrincipal;

/** Lazy connection factory: no connection or background worker at application startup. */
public final class FinanceRuntime {
    private FinanceRuntime() {}

    public static FinanceDatabase database(ServletContext context) {
        synchronized (context) {
            var configured = context.getAttribute(FinanceDatabase.class.getName());
            if (configured instanceof FinanceDatabase database) return database;
            FinanceDatabase database =
                    () ->
                            DriverManager.getConnection(
                                    DatabaseConfig.getJdbcUrl(),
                                    DatabaseConfig.getPrincipalUser(DatabasePrincipal.ADMIN),
                                    DatabaseConfig.getPrincipalPassword(DatabasePrincipal.ADMIN));
            context.setAttribute(FinanceDatabase.class.getName(), database);
            return database;
        }
    }
}
