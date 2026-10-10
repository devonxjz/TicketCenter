package vn.ticketscenter.fulfillment.checkin.integration;

import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.RedisCheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit Azure acceptance: actual restricted role, current model mappings, no shared writes. */
class CheckInEventAzureReadOnlyIT {
    @Test void restrictedCheckInRoleSupportsRepositoryAndServiceAgainstAzure() throws Exception {
        assertEquals("TicketCenterticke", DatabaseConfig.getDatabaseName());
        assertEquals("devonxjz.database.windows.net", DatabaseConfig.getHost());
        UUID event = UUID.fromString(AppConfig.get("TC_CHECKIN_TEST_EVENT_ID"));
        UUID actor;
        try (var connection = restrictedConnection()) {
        try (var query = connection.prepareStatement("""
                SELECT TOP (1) m.user_id FROM dbo.tc_organization_memberships m
                JOIN dbo.tc_users u ON u.id=m.user_id JOIN dbo.tc_events e ON e.organization_id=m.organization_id
                WHERE e.id=? AND m.active=1 AND u.status='ACTIVE' AND m.role IN ('MANAGER','CHECK_IN_STAFF')
                ORDER BY m.user_id
                """)) {
            query.setString(1, event.toString()); query.setQueryTimeout(5);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next(), "An existing active check-in membership is required; no shared fixtures are created");
                actor = UUID.fromString(rows.getString(1));
            }
        }
        Connection shared = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) return null;
                    try { return method.invoke(connection, args); }
                    catch (java.lang.reflect.InvocationTargetException failure) { throw failure.getCause(); }
                });
        DataSource dataSource = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{DataSource.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getConnection" -> shared;
                    case "isWrapperFor" -> false;
                    case "toString" -> "Azure read-only CHECK_IN role";
                    default -> null;
                });
        // Same persistence unit as production; do not disable or override schema validation.
        try (var factory = Persistence.createEntityManagerFactory("ticketscenter", Map.of("jakarta.persistence.nonJtaDataSource", dataSource))) {
            var repository = new CheckInEventRepository(new TransactionManager(Map.of(DatabasePrincipal.CHECK_IN, factory)));
            var info = repository.accessibleEvent(actor, event);
            var cache = new RedisCheckInEventCache(() -> { throw new IllegalStateException("Read-only acceptance: cache unavailable"); });
            var open = new CheckInEventService(repository, cache, Clock.fixed(info.windowStart(), ZoneOffset.UTC));
            assertTrue(open.window(actor, event).isOpen());
            assertFalse(new CheckInEventService(repository, cache, Clock.fixed(info.windowStart().minusSeconds(60), ZoneOffset.UTC)).window(actor, event).isOpen());
            assertFalse(new CheckInEventService(repository, cache, Clock.fixed(info.endTime(), ZoneOffset.UTC)).window(actor, event).isOpen());
            var summary = open.events(actor, info.organizationId()).stream().filter(e -> e.eventId().equals(event)).findFirst().orElseThrow();
            assertTrue(summary.isCheckInOpen()); assertTrue(summary.totalTicketsIssued() >= summary.totalCheckedIn());
            assertEquals(403, assertThrows(CheckInException.class, () -> open.window(UUID.randomUUID(), event)).httpStatus());
            assertEquals(404, assertThrows(CheckInException.class, () -> open.window(actor, UUID.randomUUID())).httpStatus());
            try (var query = connection.createStatement()) {
                query.setQueryTimeout(5);
                assertThrows(SQLException.class, () -> query.executeQuery("SELECT TOP (0) total_amount FROM dbo.tc_orders"));
            }
        }
        }
    }
    private static Connection restrictedConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), AppConfig.get("TC_SQL_TEST_LOGIN"), AppConfig.get("TC_SQL_TEST_PASSWORD"));
        try (var query = connection.createStatement()) {
            query.setQueryTimeout(5); query.execute("EXECUTE AS USER='tc_checkin_user'");
        } catch (SQLException failure) { connection.close(); throw failure; }
        return connection;
    }
}
