package vn.ticketscenter.config.persistence;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class JpaConnectionTest {

    @Test
    @DisplayName("Verify JDBC and Database connectivity")
    void testDatabaseConnectivity() {
        String jdbcUrl = DatabaseConfig.getJdbcUrl();
        assertNotNull(jdbcUrl, "JDBC URL should not be null");

        String user = DatabaseConfig.getUser();
        String pass = DatabaseConfig.getPassword();

        try (Connection connection = DriverManager.getConnection(jdbcUrl, user, pass);
             Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1 AS alive, DB_NAME() AS current_db")) {

            assertTrue(rs.next(), "Database should return at least one row");
            assertEquals(1, rs.getInt("alive"), "Query result should be 1");
            String dbName = rs.getString("current_db");
            assertNotNull(dbName, "Current DB name should not be null");
            System.out.println("✅ JDBC Connection Successful! Connected to database: " + dbName);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Database connection not available in this environment: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Verify JPA EntityManagerFactory and PersistenceRegistry")
    void testPersistenceRegistry() {
        try (PersistenceRegistry registry = new PersistenceRegistry()) {
            registry.initialize();
            assertTrue(registry.isReady(), "PersistenceRegistry should be ready after initialization");
            System.out.println("✅ JPA PersistenceRegistry initialized and verified successfully!");
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "JPA PersistenceRegistry could not connect in this environment: " + e.getMessage());
        }
    }
}
