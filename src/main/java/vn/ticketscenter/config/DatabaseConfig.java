package vn.ticketscenter.config;

import vn.ticketscenter.config.persistence.DatabasePrincipal;

/**
 * Cấu hình kết nối cơ sở dữ liệu Microsoft SQL Server (Azure SQL / Local).
 * Tách biệt các thiết lập về máy chủ, cổng, tên database, thông tin xác thực và timeout.
 */
public final class DatabaseConfig {

    private DatabaseConfig() {
        // Utility class không cho phép khởi tạo instance
    }

    public static String getHost() {
        String host = AppConfig.get("db.host");
        return (host != null && !host.isBlank()) ? host : AppConfig.get("TC_SQL_HOST", "localhost");
    }

    public static int getPort() {
        int port = AppConfig.getInt("db.port", 0);
        return port > 0 ? port : AppConfig.getInt("TC_SQL_PORT", 1433);
    }

    public static String getDatabaseName() {
        String dbName = AppConfig.get("db.name");
        if (dbName != null && !dbName.isBlank()) {
            return dbName;
        }
        String testDb = AppConfig.get("TC_SQL_TEST_DB");
        if (testDb != null && !testDb.isBlank()) {
            return testDb;
        }
        return AppConfig.get("TC_SQL_DEV_DB", "TicketsCenter_Dev");
    }

    public static String getUser() {
        String user = AppConfig.get("db.user");
        return (user != null && !user.isBlank()) ? user : AppConfig.get("TC_SQL_LOGIN", "sa");
    }

    public static String getPassword() {
        String pass = AppConfig.get("db.password");
        return (pass != null && !pass.isBlank()) ? pass : AppConfig.get("TC_SQL_PASSWORD", "");
    }

    public static String getPrincipalUser(DatabasePrincipal principal) {
        String user = AppConfig.get("db." + principal.name().toLowerCase() + ".user");
        return (user != null && !user.isBlank()) ? user : getUser();
    }

    public static String getPrincipalPassword(DatabasePrincipal principal) {
        String pass = AppConfig.get("db." + principal.name().toLowerCase() + ".password");
        return (pass != null && !pass.isBlank()) ? pass : getPassword();
    }

    public static boolean isEncrypt() {
        String enc = AppConfig.get("db.encrypt");
        if (enc != null && !enc.isBlank()) {
            return Boolean.parseBoolean(enc);
        }
        return AppConfig.getBoolean("TC_SQL_ENCRYPT", true);
    }

    public static boolean isTrustServerCertificate() {
        String trust = AppConfig.get("db.trustServerCertificate");
        if (trust != null && !trust.isBlank()) {
            return Boolean.parseBoolean(trust);
        }
        return AppConfig.getBoolean("TC_SQL_TRUST_SERVER_CERT", false);
    }

    public static int getLoginTimeout() {
        int timeout = AppConfig.getInt("db.loginTimeout", 0);
        return timeout > 0 ? timeout : AppConfig.getInt("TC_SQL_CONNECT_TIMEOUT_SEC", 30);
    }

    public static String getJdbcUrl() {
        String directUrl = AppConfig.get("db.url");
        if (directUrl != null && !directUrl.isBlank()) {
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
