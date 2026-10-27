package vn.ticketscenter.ticketing.dao;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.dao.RealEventVisibility;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.service.HoldException;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RealEventHoldVisibilityTest {
    private final List<String> statements = new ArrayList<>();
    private final UUID eventId = UUID.randomUUID();
    private final HoldActor actor = new HoldActor(UUID.randomUUID(), 0);
    private int openConnections, maximumOpenConnections;

    private DataSource database(boolean visible) {
        return proxy(DataSource.class, (method, args) -> {
            if (!method.equals("getConnection")) return null;
            openConnections++; maximumOpenConnections = Math.max(maximumOpenConnections, openConnections);
            return proxy(Connection.class, (name, values) -> {
                    if (name.equals("close")) { openConnections--; return null; }
                    if (!name.equals("prepareStatement")) return null;
                    String sql = (String) values[0]; statements.add(sql);
                    boolean[] consumed = {false};
                    return proxy(PreparedStatement.class, (operation, parameters) -> {
                        switch (operation) {
                            case "execute": return true;
                            case "getMoreResults": return false;
                            case "getUpdateCount": return -1;
                            case "executeQuery", "getResultSet": break;
                            default: return null;
                        }
                        return proxy(ResultSet.class, (read, columns) -> switch (read) {
                            case "next" -> { boolean result = !consumed[0] && (visible || sql.startsWith("EXEC")); consumed[0] = true; yield result; }
                            case "getString" -> sql.contains("usp_ReadActiveHold")
                                    ? "{\"serverTime\":\"2026-10-13T00:00:00Z\",\"hold\":{\"id\":\"" + eventId
                                    + "\",\"eventId\":\"" + eventId + "\",\"status\":\"ACTIVE\",\"expiresAt\":\"2026-10-13T00:05:00Z\","
                                    + "\"items\":[{\"zoneId\":\"" + eventId + "\",\"seatId\":null,\"quantity\":1,\"unitPrice\":\"100000\"}]}}" : "{}";
                            default -> null;
                        });
                    });
                });
        });
    }

    @Test void excludedEventCannotReachHoldOptionsOrCreateProcedure() {
        inRealMode(() -> {
            DataSource source = database(false);
            var repository = new HoldRepository(source, source);
            var command = new HoldCommand.Create(UUID.randomUUID(), new CreateHoldRequest(eventId,
                    List.of(new HoldSelection(UUID.randomUUID(), null, 1))));
            assertEquals(404, assertThrows(HoldException.class, () -> repository.options(actor, eventId)).status());
            assertEquals(404, assertThrows(HoldException.class, () -> repository.mutate(actor, command)).status());
            assertEquals(2, statements.size());
            assertTrue(statements.stream().allMatch(sql -> sql.startsWith("SELECT") && sql.contains(RealEventVisibility.predicate("e"))));
        });
    }

    @Test void visibleEventStillReachesAuthorizedOptionsProcedure() {
        inRealMode(() -> {
            DataSource source = database(true);
            assertEquals("{}", new HoldRepository(source, source).options(actor, eventId));
            assertEquals(2, statements.size());
            assertTrue(statements.get(1).startsWith("EXEC dbo.usp_ReadHoldOptions"));
        });
    }

    @Test void existingSyntheticHoldIsBlockedWithoutCancellingOrChangingInventory() {
        inRealMode(() -> {
            DataSource source = database(false);
            var repository = new HoldRepository(source, source);
            assertEquals(404, assertThrows(HoldException.class, () -> repository.current(actor)).status());
            assertEquals(2, statements.size());
            assertTrue(statements.getFirst().startsWith("EXEC dbo.usp_ReadActiveHold"));
            assertTrue(statements.get(1).startsWith("SELECT"));
            assertEquals(1, maximumOpenConnections, "Visibility read must not nest a second pool checkout");
            assertEquals(0, openConnections);
        });
    }

    @Test void demoFixtureModeDoesNotRequireAnExtraPublicViewQuery() {
        String previous = System.getProperty("app.data.mode");
        String previousEnv = System.getProperty("app.env");
        System.setProperty("app.data.mode", "demo"); System.setProperty("app.env", "test");
        try {
            DataSource source = database(false);
            assertEquals("{}", new HoldRepository(source, source).options(actor, eventId));
            assertEquals(1, statements.size());
            assertTrue(statements.getFirst().startsWith("EXEC"));
        } finally { restore("app.data.mode", previous); restore("app.env", previousEnv); }
    }

    private static void inRealMode(Runnable work) {
        String previous = System.getProperty("app.data.mode");
        System.setProperty("app.data.mode", "real");
        try { work.run(); } finally { restore("app.data.mode", previous); }
    }
    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
    @FunctionalInterface private interface Invocation { Object call(String method, Object[] args); }
    private static <T> T proxy(Class<T> type, Invocation work) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (instance, method, args) -> work.call(method.getName(), args)));
    }
}
