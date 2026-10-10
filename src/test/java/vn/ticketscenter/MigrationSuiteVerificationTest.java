package vn.ticketscenter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import vn.ticketscenter.config.DatabaseConfig;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("db")
public class MigrationSuiteVerificationTest {

    private static final String[] MIGRATION_FILES = {
            "database/migrations/001_create_tables.sql",
            "database/migrations/002_create_relationships_and_constraints.sql",
            "database/migrations/003_create_indexes.sql",
            "database/migrations/004_create_functions.sql",
            "database/migrations/005_create_views.sql",
            "database/migrations/006_create_procedures.sql",
            "database/migrations/007_create_triggers.sql",
            "database/migrations/008_create_runtime_roles_and_permissions.sql"
    };

    @Test
    @DisplayName("Execute all 8 rewritten migration scripts and verify catalog completeness")
    void executeAndVerifyFullMigrationSuite() throws Exception {
        String jdbcUrl = DatabaseConfig.getJdbcUrl();
        String user = DatabaseConfig.getUser();
        String pass = DatabaseConfig.getPassword();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, pass)) {
            // Ensure target is TicketCenterticke
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT DB_NAME() AS current_db")) {
                assertTrue(rs.next());
                assertEquals("TicketCenterticke", rs.getString("current_db"));
            }

            System.out.println("=== 1. EXECUTING MIGRATION SCRIPTS 001 - 008 ===");
            for (String fileStr : MIGRATION_FILES) {
                Path filePath = Path.of(fileStr);
                assertTrue(Files.exists(filePath), "Migration file must exist: " + fileStr);

                String sqlContent = Files.readString(filePath, StandardCharsets.UTF_8);
                String[] batches = sqlContent.split("(?im)^GO\\s*$");

                int batchCount = 0;
                for (String batch : batches) {
                    String trimmed = batch.trim();
                    if (trimmed.isEmpty()) continue;
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(trimmed);
                        batchCount++;
                    } catch (Exception e) {
                        System.err.println("FAILED in file: " + fileStr);
                        System.err.println("Batch content: " + trimmed);
                        throw e;
                    }
                }
                System.out.printf("✅ Successfully executed %s (%d batches)%n", fileStr, batchCount);
            }

            System.out.println("\n=== 2. VERIFYING DATABASE CATALOG COMPLETENESS ===");

            try (Statement stmt = conn.createStatement()) {
                // 1. Tables count (excluding system tables)
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE = 'BASE TABLE'")) {
                    assertTrue(rs.next());
                    int tableCount = rs.getInt("total");
                    System.out.println("Table count: " + tableCount);
                    assertEquals(35, tableCount, "Expected exactly 35 tables (34 domain + tc_schema_migrations)");
                }

                // 2. Foreign keys count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.foreign_keys WHERE is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int fkCount = rs.getInt("total");
                    System.out.println("Foreign keys count: " + fkCount);
                    assertEquals(59, fkCount, "Expected exactly 59 foreign keys");
                }

                // 3. Unique constraints count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.key_constraints WHERE type = 'UQ' AND is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int uqCount = rs.getInt("total");
                    System.out.println("Unique constraints count: " + uqCount);
                    assertEquals(20, uqCount, "Expected exactly 20 unique constraints");
                }

                // 4. Primary keys count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.key_constraints WHERE type = 'PK' AND is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int pkCount = rs.getInt("total");
                    System.out.println("Primary keys count: " + pkCount);
                    assertEquals(35, pkCount, "Expected exactly 35 primary keys");
                }

                // 5. Check constraints count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.check_constraints WHERE is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int ckCount = rs.getInt("total");
                    System.out.println("Check constraints count: " + ckCount);
                    assertEquals(58, ckCount, "Expected exactly 58 check constraints");
                }

                // 6. User-defined functions count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.objects WHERE type IN ('FN', 'IF', 'TF') AND is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int fnCount = rs.getInt("total");
                    System.out.println("Functions count: " + fnCount);
                    assertEquals(11, fnCount, "Expected exactly 11 functions");
                }

                // 7. Business views count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.views WHERE is_ms_shipped = 0 AND name NOT LIKE 'database_firewall%'")) {
                    assertTrue(rs.next());
                    int viewCount = rs.getInt("total");
                    System.out.println("Views count: " + viewCount);
                    assertEquals(16, viewCount, "Expected exactly 16 business views");
                }

                // 8. Stored procedures count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.procedures WHERE is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int procCount = rs.getInt("total");
                    System.out.println("Stored procedures count: " + procCount);
                    assertEquals(24, procCount, "Expected exactly 24 stored procedures");
                }

                // 9. Triggers count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.triggers WHERE is_ms_shipped = 0")) {
                    assertTrue(rs.next());
                    int trigCount = rs.getInt("total");
                    System.out.println("Triggers count: " + trigCount);
                    assertEquals(19, trigCount, "Expected exactly 19 triggers");
                }

                // 10. Runtime roles count
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) AS total FROM sys.database_principals " +
                        "WHERE type = 'R' AND name IN ('tc_auth', 'tc_buyer', 'tc_checkin', 'tc_manager', 'tc_platform_admin', 'tc_worker')")) {
                    assertTrue(rs.next());
                    int roleCount = rs.getInt("total");
                    System.out.println("Runtime roles count: " + roleCount);
                    assertEquals(6, roleCount, "Expected exactly 6 runtime roles");
                }
            }
            System.out.println("✅ All catalog verification assertions passed successfully!");
        }
    }
}
