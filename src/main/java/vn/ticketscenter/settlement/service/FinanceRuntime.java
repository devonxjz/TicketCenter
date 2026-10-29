package vn.ticketscenter.settlement.service;

import java.util.Optional;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import jakarta.servlet.ServletContext;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.FeatureContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static vn.ticketscenter.config.persistence.DatabasePrincipal.ADMIN;

/**
 * Lazy connection factory: pooled via PersistenceRegistry, with fallback to DriverManager.
 * Finance procedures own their transactions, so every connection handed out is in autocommit mode;
 * the shared pools default to manual commit for JPA, and the pool restores that when the connection closes.
 */
public final class FinanceRuntime {
    private FinanceRuntime() {}

    public static FinanceDatabase database(ServletContext context) {
        return FeatureContext.cached(context, FinanceDatabase.class.getName(), () -> create(context));
    }

    private static FinanceDatabase create(ServletContext context) {
        Optional<PersistenceRegistry> registry = FeatureContext.registry(context).filter(pool -> pool.hasPrincipal(ADMIN));
        if (registry.isPresent()) {
            return autoCommitting(registry.get().dataSource(ADMIN));
        }
        return () ->
                DriverManager.getConnection(
                        DatabaseConfig.getJdbcUrl(),
                        DatabaseConfig.getPrincipalUser(ADMIN),
                        DatabaseConfig.getPrincipalPassword(ADMIN));
    }

    static FinanceDatabase autoCommitting(DataSource pool) {
        return () -> {
            Connection connection = pool.getConnection();
            try {
                connection.setAutoCommit(true);
                return connection;
            } catch (SQLException failure) {
                connection.close();
                throw failure;
            }
        };
    }
}
