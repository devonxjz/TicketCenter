package vn.ticketscenter.config.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.error.AppException;

class SqlCallTest {

    @Test
    void bindsArgumentsCorrectly() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-18T10:15:30Z");
        BigDecimal amount = new BigDecimal("250000");

        Map<Integer, Object> boundParams = new HashMap<>();
        AtomicInteger queryTimeout = new AtomicInteger();

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("setString".equals(name)) {
                        boundParams.put((Integer) args[0], args[1]);
                        return null;
                    }
                    if ("setObject".equals(name)) {
                        boundParams.put((Integer) args[0], args[1]);
                        return null;
                    }
                    if ("setBigDecimal".equals(name)) {
                        boundParams.put((Integer) args[0], args[1]);
                        return null;
                    }
                    if ("setNull".equals(name)) {
                        boundParams.put((Integer) args[0], null);
                        return null;
                    }
                    if ("setQueryTimeout".equals(name)) {
                        queryTimeout.set((Integer) args[0]);
                        return null;
                    }
                    if ("execute".equals(name)) return false;
                    if ("getMoreResults".equals(name)) return false;
                    if ("getUpdateCount".equals(name)) return -1;
                    if ("close".equals(name)) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> "prepareStatement".equals(method.getName()) ? statement : null);

        SqlCall.call(connection, "EXEC usp_Test ?,?,?,?,?", rs -> null, null, id, now, amount, null, "active");

        assertEquals(20, queryTimeout.get());
        assertEquals(id.toString(), boundParams.get(1));
        assertEquals(LocalDateTime.ofInstant(now, ZoneOffset.UTC), boundParams.get(2));
        assertEquals(amount, boundParams.get(3));
        assertNull(boundParams.get(4));
        assertEquals("active", boundParams.get(5));
    }

    @Test
    void extractsRowsAndDrainsLateResults() {
        AtomicInteger moreResultsCalls = new AtomicInteger();

        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) return true;
                    if ("getString".equals(method.getName())) return "ticket-123";
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("execute".equals(name)) return true;
                    if ("getResultSet".equals(name)) return resultSet;
                    if ("getMoreResults".equals(name)) {
                        // simulate one extra result then done
                        return moreResultsCalls.incrementAndGet() < 2;
                    }
                    if ("getUpdateCount".equals(name)) return -1;
                    if ("close".equals(name)) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> statement);

        String result = SqlCall.call(connection, "EXEC usp_GetTicket", rs -> rs.getString(1), null);

        assertEquals("ticket-123", result);
        assertTrue(moreResultsCalls.get() >= 2, "Must drain all additional results");
    }

    @Test
    void translatesMappedSqlErrorCodesToAppException() {
        SQLException sqlException = new SQLException("Invalid organization state", "45000", 52101);

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) throw sqlException;
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> statement);

        SqlCall.ErrorTable errors = new SqlCall.ErrorTable()
                .on(52101, 400, "ORG_INVALID_STATE", "Organization is not active");

        AppException thrown = assertThrows(AppException.class, () ->
                SqlCall.execute(connection, "EXEC usp_CheckOrg", errors));

        assertEquals(400, thrown.status());
        assertEquals("ORG_INVALID_STATE", thrown.code());
        assertEquals("Invalid organization state", thrown.getMessage());
    }

    @Test
    void fallsBackTo500ForUnmappedSqlError() {
        SQLException sqlException = new SQLException("Syntax error near WHERE", "42000", 102);

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) throw sqlException;
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> statement);

        AppException thrown = assertThrows(AppException.class, () ->
                SqlCall.execute(connection, "SELECT * FROM bad", null));

        assertEquals(500, thrown.status());
        assertEquals("DATABASE_ERROR", thrown.code());
    }

    @Test
    void queriesListAndMapsMultipleRows() {
        AtomicInteger cursor = new AtomicInteger(-1);
        List<String> mockData = List.of("alpha", "beta", "gamma");

        ResultSet resultSet = (ResultSet) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> {
                    if ("next".equals(method.getName())) {
                        return cursor.incrementAndGet() < mockData.size();
                    }
                    if ("getString".equals(method.getName())) {
                        return mockData.get(cursor.get());
                    }
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) return true;
                    if ("getResultSet".equals(method.getName())) return resultSet;
                    if ("getMoreResults".equals(method.getName())) return false;
                    if ("getUpdateCount".equals(method.getName())) return -1;
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> statement);

        List<String> items = SqlCall.queryList(connection, "SELECT name FROM tbl", rs -> rs.getString(1), null);

        assertEquals(List.of("alpha", "beta", "gamma"), items);
    }

    @Test
    void usesFallbackWhenSpecified() {
        SQLException sqlException = new SQLException("Connection timed out", "08001", 50000);

        PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    if ("execute".equals(method.getName())) throw sqlException;
                    if ("close".equals(method.getName())) return null;
                    return null;
                });

        Connection connection = (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> statement);

        SqlCall.ErrorTable errors = SqlCall.ErrorTable.of()
                .fallback((code, msg) -> new AppException(503, "DB_DOWN", "Database unavailable"));

        AppException thrown = assertThrows(AppException.class, () ->
                SqlCall.execute(connection, "SELECT 1", errors));

        assertEquals(503, thrown.status());
        assertEquals("DB_DOWN", thrown.code());
    }

    @Test
    void fromCauseFindsTheFirstDomainErrorInTheChain() {
        var errors = SqlCall.ErrorTable.of()
                .on(53005, 400, "REFUND_NOT_ELIGIBLE", "Vé không đủ điều kiện hoàn trả.")
                .fallback((code, message) -> new AppException(503, "DB_DOWN", "Không khả dụng."));

        var wrappedSql = new IllegalStateException(new SQLException("", "S0001", 53005));
        assertEquals("REFUND_NOT_ELIGIBLE", errors.fromCause(wrappedSql).code());

        var wrappedDomain = new RuntimeException(new AppException(409, "CONFLICT", "Xung đột."));
        assertEquals("CONFLICT", errors.fromCause(wrappedDomain).code());

        assertEquals("DB_DOWN", errors.fromCause(new IllegalArgumentException("no principal")).code());
    }
}
