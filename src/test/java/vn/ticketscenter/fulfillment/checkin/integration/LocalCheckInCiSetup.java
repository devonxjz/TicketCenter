package vn.ticketscenter.fulfillment.checkin.integration;

import java.sql.*;
import vn.ticketscenter.config.DatabaseConfig;

/** Bootstrap only the disposable CI SQL Server; never uses the shared configuration. */
public final class LocalCheckInCiSetup {
    public static void main(String[] args) throws Exception {
        if (!"true".equals(System.getenv("CI")) || !"127.0.0.1".equals(DatabaseConfig.getHost())
                || !"TicketCenter_Khanh01_Test".equals(DatabaseConfig.getDatabaseName()))
            throw new IllegalStateException("CI setup is restricted to the disposable loopback test database");
        String password = System.getenv("DB_CHECK_IN_PASSWORD");
        if (password == null || !password.matches("[A-Za-z0-9+=/!@_-]{20,128}"))
            throw new IllegalStateException("Supply a generated CI fixture password");
        try (var connection = DriverManager.getConnection("jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=true;trustServerCertificate=true;loginTimeout=15",
                DatabaseConfig.getUser(), DatabaseConfig.getPassword()); var query = connection.createStatement()) {
            query.setQueryTimeout(15);
            query.execute("IF DB_ID('TicketCenter_Khanh01_Test') IS NOT NULL THROW 51000,'Refusing existing database',1; CREATE DATABASE TicketCenter_Khanh01_Test;");
            query.execute("CREATE LOGIN khanh01_checkin WITH PASSWORD='" + password + "', CHECK_POLICY=OFF;");
        }
        try (var connection = DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.getUser(), DatabaseConfig.getPassword());
             var query = connection.createStatement()) {
            query.setQueryTimeout(15);
            query.execute("CREATE USER khanh01_checkin FOR LOGIN khanh01_checkin;");
        }
    }
}
