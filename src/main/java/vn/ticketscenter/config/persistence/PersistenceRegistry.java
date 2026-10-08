package vn.ticketscenter.config.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import vn.ticketscenter.config.DatabaseConfig;

import java.util.EnumMap;
import java.util.Map;

public final class PersistenceRegistry implements AutoCloseable {

    private final Map<DatabasePrincipal, EntityManagerFactory> factories = new EnumMap<>(DatabasePrincipal.class);
    private final Map<DatabasePrincipal, HikariDataSource> pools = new EnumMap<>(DatabasePrincipal.class);

    public void initialize() {
        for (DatabasePrincipal principal : DatabasePrincipal.values()) {
            String user = DatabaseConfig.getPrincipalUser(principal);
            String password = DatabaseConfig.getPrincipalPassword(principal);
            if (user == null || password == null) continue;

            HikariConfig pool = new HikariConfig();
            pool.setPoolName("ticketscenter-" + principal.name().toLowerCase());
            pool.setDriverClassName("com.microsoft.sqlserver.jdbc.SQLServerDriver");
            pool.setJdbcUrl(DatabaseConfig.getJdbcUrl());
            pool.setUsername(user);
            pool.setPassword(password);
            pool.setMinimumIdle(0);
            pool.setMaximumPoolSize(1);
            pool.setConnectionTimeout(DatabaseConfig.getLoginTimeout() * 1000L);
            pool.setInitializationFailTimeout(-1);
            pool.setAutoCommit(false);
            HikariDataSource dataSource = new HikariDataSource(pool);
            try {
                factories.put(principal, Persistence.createEntityManagerFactory("ticketscenter", Map.of(
                        "jakarta.persistence.nonJtaDataSource", new ConnectionBudgetDataSource(dataSource))));
                pools.put(principal, dataSource);
            } catch (RuntimeException exception) {
                dataSource.close();
                throw exception;
            }
        }
    }

    public TransactionManager transactionManager() {
        return new TransactionManager(factories);
    }

    public boolean hasPrincipal(DatabasePrincipal principal) {
        return factories.containsKey(principal);
    }

    public boolean isReady() {
        if (factories.isEmpty()) return false;
        try {
            for (EntityManagerFactory factory : factories.values()) {
                var entityManager = factory.createEntityManager();
                try {
                    entityManager.createNativeQuery("SELECT 1", Integer.class).getSingleResult();
                } finally {
                    entityManager.close();
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    public void close() {
        factories.values().forEach(EntityManagerFactory::close);
        factories.clear();
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
