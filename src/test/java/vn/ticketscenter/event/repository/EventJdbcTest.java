package vn.ticketscenter.event.repository;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import vn.ticketscenter.event.EventException;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

class EventJdbcTest {
    private static final class Database {
        final List<String> calls = new ArrayList<>();
        boolean ambiguousCommit;

        DataSource source() {
            Connection connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        calls.add(method.getName());
                                        if (method.getName().equals("commit") && ambiguousCommit)
                                            throw new SQLException(
                                                    "Connection lost after server commit");
                                        return null;
                                    });
            return (DataSource)
                    Proxy.newProxyInstance(
                            DataSource.class.getClassLoader(),
                            new Class<?>[] {DataSource.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("getConnection")) return connection;
                                throw new AssertionError("Unexpected DataSource method");
                            });
        }
    }

    @Test
    void resultIsReturnedAfterCommitAndConnectionCloses() {
        Database database = new Database();
        assertEquals("committed", EventJdbc.tx(database.source(), connection -> "committed"));
        assertEquals(List.of("setAutoCommit", "commit", "close"), database.calls);
    }

    @Test
    void permissionFailureRollsBackAndDoesNotCommit() {
        Database database = new Database();
        EventException error =
                assertThrows(
                        EventException.class,
                        () ->
                                EventJdbc.tx(
                                        database.source(),
                                        connection -> {
                                            throw new EventException(
                                                    403, "EVENT_FORBIDDEN", "Revoked");
                                        }));
        assertEquals(403, error.status());
        assertEquals(List.of("setAutoCommit", "rollback", "close"), database.calls);
    }

    @Test
    void unknownCommitReturnsRetryableFailureAndNeverClaimsSuccess() {
        Database database = new Database();
        database.ambiguousCommit = true;
        EventException error =
                assertThrows(
                        EventException.class,
                        () -> EventJdbc.tx(database.source(), connection -> "not confirmed"));
        assertEquals(503, error.status());
        assertEquals("EVENT_DATABASE_UNAVAILABLE", error.code());
        assertEquals(List.of("setAutoCommit", "commit", "rollback", "close"), database.calls);
    }

    @Test
    void statementFailureRollsBackAndReturnsRetryableFailure() {
        Database database = new Database();
        EventException error =
                assertThrows(
                        EventException.class,
                        () ->
                                EventJdbc.tx(
                                        database.source(),
                                        connection -> {
                                            throw new SQLException("Constraint failure");
                                        }));
        assertEquals(503, error.status());
        assertEquals(List.of("setAutoCommit", "rollback", "close"), database.calls);
    }
}
