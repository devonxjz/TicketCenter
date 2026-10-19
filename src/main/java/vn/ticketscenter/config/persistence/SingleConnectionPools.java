package vn.ticketscenter.config.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import vn.ticketscenter.config.DatabaseConfig;

public final class SingleConnectionPools {
    private SingleConnectionPools() {}

    public static HikariDataSource open(String poolName, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
        config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setInitializationFailTimeout(-1);
        config.setConnectionTimeout(10_000);
        config.setAutoCommit(true);
        return new HikariDataSource(config);
    }
}
