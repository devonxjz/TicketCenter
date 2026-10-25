package vn.ticketscenter.config.persistence;

import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.hibernate.Session;
import vn.ticketscenter.config.error.AppException;

/**
 * Single, unified database call executor for Stored Procedures and Views.
 *
 * Enforces consistent UTC Instant binding, UUID string formatting, statement timeouts,
 * exhaustive multi-result draining to surface late SQL THROW errors, and structured
 * SQL error code mapping to domain-specific {@link AppException} instances.
 */
public final class SqlCall {

    private static final int DEFAULT_TIMEOUT_SECONDS = 20;

    @FunctionalInterface
    public interface ResultSetExtractor<T> {
        T extract(ResultSet rows) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    public record OutParam(int sqlType) {
        public static OutParam of(int sqlType) {
            return new OutParam(sqlType);
        }
    }

    /**
     * Immutable mapping table converting SQL Server error codes into specific {@link AppException} instances.
     */
    public static final class ErrorTable {
        private final Map<Integer, BiFunction<Integer, String, AppException>> mapping = new HashMap<>();
        private BiFunction<Integer, String, AppException> fallback;

        public static ErrorTable of() {
            return new ErrorTable();
        }

        public ErrorTable on(int code, int httpStatus, String errorCode, String defaultMessage) {
            mapping.put(code, (c, msg) -> new AppException(httpStatus, errorCode, msg != null && !msg.isBlank() ? msg : defaultMessage));
            return this;
        }

        public ErrorTable on(int[] codes, int httpStatus, String errorCode, String defaultMessage) {
            for (int code : codes) {
                on(code, httpStatus, errorCode, defaultMessage);
            }
            return this;
        }

        public ErrorTable on(int code, Function<String, AppException> factory) {
            mapping.put(code, (c, msg) -> factory.apply(msg));
            return this;
        }

        public ErrorTable fallback(BiFunction<Integer, String, AppException> fallback) {
            this.fallback = fallback;
            return this;
        }

        public AppException translate(int code, String message) {
            var factory = mapping.get(code);
            if (factory != null) {
                return factory.apply(code, message);
            }
            if (fallback != null) {
                return fallback.apply(code, message);
            }
            return null;
        }
    }

    private SqlCall() {}

    /**
     * Executes work inside an explicit transaction on the provided DataSource.
     */
    public static <T> T inTransaction(DataSource dataSource, SqlWork<T> work, ErrorTable errors) {
        Objects.requireNonNull(dataSource, "dataSource must not be null");
        Objects.requireNonNull(work, "work must not be null");
        try (Connection connection = dataSource.getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException failure) {
            throw translateError(failure, errors);
        }
    }

    /** Runs JDBC work on the connection of the principal's shared JPA transaction. */
    public static <T> T inSession(TransactionManager transactions, DatabasePrincipal principal, SqlWork<T> work) {
        return transactions.execute(principal, em -> em.unwrap(Session.class).doReturningWork(work::run));
    }

    /**
     * The {@link AppException} a failed call surfaces as: one already raised in its cause chain, the first SQL
     * error {@code errors} knows, or {@code unavailable}.
     */
    public static AppException failure(
            RuntimeException failure, ErrorTable errors, Supplier<? extends AppException> unavailable) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof AppException app) return app;
            if (cause instanceof SQLException sql) {
                AppException app = errors.translate(sql.getErrorCode(), sql.getMessage());
                if (app != null) return app;
            }
        }
        return unavailable.get();
    }

    /** Nullable UUID column stored as text. */
    public static UUID uuid(ResultSet row, String column) throws SQLException {
        String value = row.getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    /** Nullable UTC timestamp column. */
    public static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return value == null ? null : value.toInstant();
    }

    /**
     * Executes a SQL command or Stored Procedure, returning the mapped result from the first result set.
     */
    public static <T> T call(Connection connection, String sql, ResultSetExtractor<T> extractor, ErrorTable errors, Object... args) {
        return callWithTimeout(connection, sql, DEFAULT_TIMEOUT_SECONDS, extractor, errors, args);
    }

    /**
     * Executes a SQL command or Stored Procedure with an explicit query timeout in seconds.
     */
    public static <T> T callWithTimeout(
            Connection connection,
            String sql,
            int timeoutSeconds,
            ResultSetExtractor<T> extractor,
            ErrorTable errors,
            Object... args) {
        Objects.requireNonNull(connection, "connection must not be null");
        Objects.requireNonNull(sql, "sql must not be null");

        try (PreparedStatement statement = sql.stripLeading().startsWith("{")
                ? connection.prepareCall(sql)
                : connection.prepareStatement(sql)) {
            if (timeoutSeconds > 0) {
                statement.setQueryTimeout(timeoutSeconds);
            }
            bindArguments(statement, args);

            boolean hasResults = statement.execute();
            while (!hasResults && statement.getUpdateCount() != -1) {
                hasResults = statement.getMoreResults();
            }

            T result = null;
            if (hasResults) {
                try (ResultSet rows = statement.getResultSet()) {
                    if (rows != null && rows.next() && extractor != null) {
                        result = extractor.extract(rows);
                    }
                }
            }

            drainLateErrors(statement);
            return result;
        } catch (SQLException failure) {
            throw translateError(failure, errors);
        }
    }

    /**
     * Executes a query returning a list of mapped rows.
     */
    public static <T> List<T> queryList(
            Connection connection,
            String sql,
            RowMapper<T> mapper,
            ErrorTable errors,
            Object... args) {
        List<T> list = call(connection, sql, rows -> {
            List<T> items = new ArrayList<>();
            do {
                items.add(mapper.map(rows));
            } while (rows.next());
            return List.copyOf(items);
        }, errors, args);
        return list != null ? list : List.of();
    }

    /**
     * Executes a query returning an optional single mapped row.
     */
    public static <T> Optional<T> queryOptional(
            Connection connection,
            String sql,
            RowMapper<T> mapper,
            ErrorTable errors,
            Object... args) {
        T result = call(connection, sql, mapper::map, errors, args);
        return Optional.ofNullable(result);
    }

    /**
     * Executes a command or Stored Procedure that does not return an entity (e.g. action commands).
     */
    public static void execute(Connection connection, String sql, ErrorTable errors, Object... args) {
        call(connection, sql, rows -> null, errors, args);
    }

    private static void bindArguments(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null || args.length == 0) return;
        for (int i = 0; i < args.length; i++) {
            int parameterIndex = i + 1;
            Object arg = args[i];

            if (arg == null) {
                statement.setNull(parameterIndex, Types.NULL);
            } else if (arg instanceof OutParam outParam) {
                if (statement instanceof CallableStatement callable) {
                    callable.registerOutParameter(parameterIndex, outParam.sqlType());
                } else {
                    statement.setNull(parameterIndex, outParam.sqlType());
                }
            } else if (arg instanceof UUID id) {
                statement.setString(parameterIndex, id.toString());
            } else if (arg instanceof Instant instant) {
                statement.setObject(parameterIndex, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
            } else if (arg instanceof Enum<?> e) {
                statement.setString(parameterIndex, e.name());
            } else if (arg instanceof BigDecimal amount) {
                statement.setBigDecimal(parameterIndex, amount);
            } else {
                statement.setObject(parameterIndex, arg);
            }
        }
    }

    private static void drainLateErrors(PreparedStatement statement) throws SQLException {
        while (statement.getMoreResults() || statement.getUpdateCount() != -1) {
            try (ResultSet extra = statement.getResultSet()) {
                // Drain any additional result set to surface any subsequent THROW statement
            }
        }
    }

    private static AppException translateError(SQLException failure, ErrorTable errors) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            if (errors != null) {
                AppException domain = errors.translate(error.getErrorCode(), error.getMessage());
                if (domain != null) return domain;
            }
        }
        return new AppException(500, "DATABASE_ERROR", failure.getMessage(), failure);
    }
}
