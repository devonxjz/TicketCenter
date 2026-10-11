package vn.ticketscenter.config.persistence;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

final class ConnectionBudgetDataSource implements DataSource {

    private static final Semaphore BUDGET = new Semaphore(vn.ticketscenter.config.DatabaseConfig.getPoolBudget(), true);
    private final DataSource delegate;

    ConnectionBudgetDataSource(DataSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return bounded(delegate::getConnection);
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return bounded(() -> delegate.getConnection(username, password));
    }

    private Connection bounded(ConnectionSupplier supplier) throws SQLException {
        try {
            BUDGET.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for database capacity", exception);
        }
        try {
            Connection connection = supplier.get();
            final boolean sqlServer;
            try {
                sqlServer = connection.getMetaData() != null
                        && connection.getMetaData().getDatabaseProductName().contains("SQL Server");
                if (sqlServer) setActor(connection, DatabaseActorContext.current());
            } catch (SQLException exception) { connection.close(); throw exception; }
            AtomicBoolean released = new AtomicBoolean();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if ("close".equals(method.getName())) {
                            if (released.compareAndSet(false, true)) {
                                try { if (sqlServer) setActor(connection, null); }
                                finally { try { connection.close(); } finally { BUDGET.release(); } }
                            }
                            return null;
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (java.lang.reflect.InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
        } catch (SQLException exception) {
            BUDGET.release();
            throw exception;
        }
    }

    private static void setActor(Connection connection, java.util.UUID actor) throws SQLException {
        try (var command = connection.prepareStatement(
                "EXEC sys.sp_set_session_context @key=N'actor_id',@value=?")) {
            command.setQueryTimeout(10);
            command.setString(1, actor == null ? null : actor.toString());
            command.execute();
        }
    }

    @FunctionalInterface private interface ConnectionSupplier { Connection get() throws SQLException; }
    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return iface.isInstance(this) ? iface.cast(this) : delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return iface.isInstance(this) || delegate.isWrapperFor(iface); }
}
