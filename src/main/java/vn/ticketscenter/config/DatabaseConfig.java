package vn.ticketscenter.config;

import vn.ticketscenter.config.persistence.DatabasePrincipal;

/**
 * Connection settings for Microsoft SQL Server (Azure SQL or local).
 * Host, port, database name, credentials and timeouts are read separately.
 */
public final class DatabaseConfig {

    private DatabaseConfig() {
        // utility class
    }

    public static String getHost() {
        return AppConfig.get("db.host", AppConfig.get("TC_SQL_HOST", "localhost"));
    }

    public static int getPort() {
        int port = AppConfig.getInt("db.port", 0);
        return port > 0 ? port : AppConfig.getInt("TC_SQL_PORT", 1433);
    }

    public static String getDatabaseName() {
        return AppConfig.get(
                "db.name", AppConfig.get("TC_SQL_TEST_DB", AppConfig.get("TC_SQL_DEV_DB", "TicketsCenter_Dev")));
    }

    public static String getUser() {
        return AppConfig.get("db.user", AppConfig.get("TC_SQL_LOGIN", "sa"));
    }

    public static String getPassword() {
        return AppConfig.get("db.password", AppConfig.get("TC_SQL_PASSWORD", ""));
    }

    public static String getPrincipalUser(DatabasePrincipal principal) {
        return AppConfig.get("db." + principal.name().toLowerCase() + ".user", getUser());
    }

    public static String getPrincipalPassword(DatabasePrincipal principal) {
        return AppConfig.get("db." + principal.name().toLowerCase() + ".password", getPassword());
    }

    public static boolean isEncrypt() {
        return AppConfig.getBoolean("db.encrypt", AppConfig.getBoolean("TC_SQL_ENCRYPT", true));
    }

    public static boolean isTrustServerCertificate() {
        return AppConfig.getBoolean(
                "db.trustServerCertificate", AppConfig.getBoolean("TC_SQL_TRUST_SERVER_CERT", false));
    }

    public static int getLoginTimeout() {
        int timeout = AppConfig.getInt("db.loginTimeout", 0);
        return timeout > 0 ? timeout : AppConfig.getInt("TC_SQL_CONNECT_TIMEOUT_SEC", 30);
    }

    public static String getJdbcUrl() {
        String directUrl = AppConfig.get("db.url");
        if (directUrl != null) {
            return directUrl;
        }
        return String.format(
                "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=%b;trustServerCertificate=%b;loginTimeout=%d;",
                getHost(),
                getPort(),
                getDatabaseName(),
                isEncrypt(),
                isTrustServerCertificate(),
                getLoginTimeout());
    }

    public static int getMaximumPoolSize() {
        return AppConfig.getInt(
                "hikari.maximum-pool-size", AppConfig.getInt("db.pool.max-size", 20));
    }

    public static int getMinimumIdle() {
        return AppConfig.getInt("hikari.minimum-idle", AppConfig.getInt("db.pool.min-idle", 10));
    }

    public static long getIdleTimeout() {
        return AppConfig.getInt("hikari.idle-timeout", 600000);
    }

    public static long getConnectionTimeout() {
        return AppConfig.getInt("hikari.connection-timeout", getLoginTimeout() * 1000);
    }

    public static long getMaxLifetime() {
        return AppConfig.getInt("hikari.max-lifetime", 1800000);
    }

    public static int getPoolBudget() {
        // One pool per database principal shares this many connections in total.
        return AppConfig.getInt("hikari.pool-budget", getMaximumPoolSize() * 3);
    }
}
