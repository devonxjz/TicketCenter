package vn.ticketscenter.settlement.service;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FinanceRuntimeTest {

    /** The shared pools hand out manual-commit connections; FinanceRepository.admin() refuses those. */
    @Test
    void pooledConnectionsAreSwitchedToAutoCommit() throws Exception {
        AtomicBoolean autoCommit = new AtomicBoolean(false);
        Connection connection =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) ->
                                        switch (method.getName()) {
                                            case "setAutoCommit" -> {
                                                autoCommit.set((Boolean) args[0]);
                                                yield null;
                                            }
                                            case "getAutoCommit" -> autoCommit.get();
                                            default -> null;
                                        });
        DataSource pool =
                (DataSource)
                        Proxy.newProxyInstance(
                                DataSource.class.getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, args) ->
                                        method.getName().equals("getConnection") ? connection : null);

        assertTrue(FinanceRuntime.autoCommitting(pool).open().getAutoCommit());
    }
}
