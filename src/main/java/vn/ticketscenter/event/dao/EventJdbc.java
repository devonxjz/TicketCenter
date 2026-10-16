package vn.ticketscenter.event.dao;

import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.EventDto;

import java.sql.*;
import java.time.*;
import java.util.*;

import javax.sql.DataSource;

final class EventJdbc {
    private EventJdbc() {}

    static List<EventDto> events(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement query = query(connection, sql, args);
                ResultSet rows = query.executeQuery()) {
            List<EventDto> result = new ArrayList<>();
            while (rows.next()) result.add(event(rows));
            return List.copyOf(result);
        }
    }

    static EventDto event(ResultSet rows) throws SQLException {
        return new EventDto(
                id(rows, 1),
                id(rows, 2),
                id(rows, 3),
                rows.getString(4),
                rows.getString(5),
                rows.getString(6),
                rows.getString(7),
                instant(rows, 8),
                instant(rows, 9),
                instant(rows, 10),
                instant(rows, 11),
                rows.getString(12),
                rows.getLong(13),
                rows.getString(14),
                rows.getString(15));
    }

    static UUID id(ResultSet rows, int column) throws SQLException {
        return UUID.fromString(rows.getString(column));
    }

    static Instant instant(ResultSet rows, int column) throws SQLException {
        if (rows.getMetaData().getColumnType(column) == -155)
            return rows.getObject(column, OffsetDateTime.class).toInstant();
        return rows.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    static PreparedStatement query(Connection connection, String sql, Object... args)
            throws SQLException {
        PreparedStatement query = connection.prepareStatement(sql);
        query.setQueryTimeout(15);
        for (int index = 0; index < args.length; index++) {
            Object value = args[index];
            if (value instanceof UUID id) query.setString(index + 1, id.toString());
            else if (value instanceof Instant instant)
                query.setObject(index + 1, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
            else query.setObject(index + 1, value);
        }
        return query;
    }

    static int execute(Connection connection, String sql, Object... args) throws SQLException {
        try (PreparedStatement query = query(connection, sql, args)) {
            return query.executeUpdate();
        }
    }

    /** Runs a procedure and returns every row of its first result set (later results are drained so errors surface). */
    static List<Object[]> rows(Connection connection, String sql, Object... args)
            throws SQLException {
        try (PreparedStatement call = query(connection, sql, args)) {
            List<Object[]> result = null;
            boolean isResultSet = call.execute();
            while (isResultSet || call.getUpdateCount() != -1) {
                if (isResultSet) {
                    try (ResultSet rows = call.getResultSet()) {
                        if (result == null) {
                            result = new ArrayList<>();
                            int columns = rows.getMetaData().getColumnCount();
                            while (rows.next()) {
                                Object[] row = new Object[columns];
                                for (int index = 0; index < columns; index++)
                                    row[index] = rows.getObject(index + 1);
                                result.add(row);
                            }
                        }
                    }
                }
                isResultSet = call.getMoreResults();
            }
            return result == null ? List.of() : result;
        }
    }

    static Object[] row(Connection connection, String sql, Object... args) throws SQLException {
        List<Object[]> rows = rows(connection, sql, args);
        if (rows.size() != 1) throw new SQLException("Procedure returned " + rows.size() + " rows");
        return rows.getFirst();
    }

    static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    /** THROW numbers raised by the usp_EventImage* procedures (migration 055). */
    private static EventException procedureFailure(int code) {
        return switch (code) {
            case 52701 -> failure(404, "EVENT_NOT_FOUND", "Event was not found");
            case 52703 -> failure(401, "SESSION_EXPIRED", "Current session is no longer valid");
            case 52704 -> failure(403, "EVENT_FORBIDDEN", "Current Manager membership is required");
            case 52705 -> failure(403, "ORGANIZATION_NOT_APPROVED", "Organization is not approved");
            case 52706 ->
                    failure(
                            403,
                            "MAINTENANCE_FORBIDDEN",
                            "Database owner connection is required for maintenance");
            case 52707 ->
                    failure(
                            409,
                            "IDEMPOTENCY_CONFLICT",
                            "Request key was already used for different input");
            case 52708 ->
                    failure(
                            409,
                            "REQUEST_ABORTED",
                            "Save has been cancelled; use a new request key");
            case 52709 ->
                    failure(409, "REQUEST_IN_PROGRESS", "Save is still running; retry shortly");
            case 52710 -> failure(400, "CATEGORY_NOT_FOUND", "Unknown category");
            case 52711 ->
                    failure(
                            409,
                            "EVENT_VERSION_CONFLICT",
                            "Event has changed; reload before saving");
            case 52712 ->
                    failure(
                            409,
                            "EVENT_NOT_EDITABLE",
                            "Only draft or rejected Events can be edited");
            case 52713 ->
                    failure(
                            409,
                            "EVENT_NOT_EDITABLE",
                            "Event cover cannot be changed in this state");
            case 52714 -> failure(409, "REQUEST_LOST", "Save intent is missing");
            case 52715 -> failure(409, "STALE_REQUEST", "Save lease has changed; retry");
            case 52716 ->
                    failure(
                            502,
                            "IMAGE_METADATA_INVALID",
                            "Image provider returned invalid metadata");
            case 52717 -> failure(409, "ASSET_UNAVAILABLE", "Image is scheduled for cleanup");
            default -> null;
        };
    }

    @FunctionalInterface
    interface Work<T> {
        T apply(Connection connection) throws SQLException;
    }

    static <T> T tx(DataSource source, Work<T> work) {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    exception.addSuppressed(rollback);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            EventException mapped = procedureFailure(exception.getErrorCode());
            if (mapped != null) throw mapped;
            if (exception.getErrorCode() == 51810)
                throw failure(
                        403, "MANAGER_REQUIRED", "Active manager of this organization required");
            if (exception.getErrorCode() == 51813)
                throw failure(
                        409, "EVENT_VERSION_CONFLICT", "Event has changed; reload before saving");
            if (exception.getErrorCode() == 51812)
                throw failure(409, "EVENT_NOT_EDITABLE", "Only editable drafts may be changed");
            throw failure(
                    503,
                    "EVENT_DATABASE_UNAVAILABLE",
                    "Event storage is temporarily unavailable; retry with the same request key");
        }
    }

    static EventException failure(int status, String code, String message) {
        return new EventException(status, code, message);
    }
}
