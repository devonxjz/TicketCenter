package vn.ticketscenter.config.persistence;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
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

    /**
     * Immutable mapping table converting SQL Server error codes into specific {@link AppException} instances.
     */
    public static final class ErrorTable {
        private final Map<Integer, BiFunction<Integer, String, AppException>> mapping = new HashMap<>();

        public ErrorTable on(int code, int httpStatus, String errorCode, String defaultMessage) {
            mapping.put(code, (c, msg) -> new AppException(httpStatus, errorCode, msg != null && !msg.isBlank() ? msg : defaultMessage));
            return this;
        }

        public ErrorTable on(int code, Function<String, AppException> factory) {
            mapping.put(code, (c, msg) -> factory.apply(msg));
            return this;
        }

        public AppException translate(int code, String message) {
            var factory = mapping.get(code);
            return factory != null ? factory.apply(code, message) : null;
        }
    }

    private SqlCall() {}

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

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            if (timeoutSeconds > 0) {
                statement.setQueryTimeout(timeoutSeconds);
            }
            bindArguments(statement, args);

            boolean hasResults = statement.execute();
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
            } else if (arg instanceof UUID id) {
                statement.setString(parameterIndex, id.toString());
            } else if (arg instanceof Instant instant) {
                statement.setObject(parameterIndex, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
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
