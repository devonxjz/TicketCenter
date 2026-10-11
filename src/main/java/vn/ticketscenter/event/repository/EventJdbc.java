package vn.ticketscenter.event.repository;

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
            if (exception.getErrorCode() == 51810)
                throw failure(403, "MANAGER_REQUIRED", "Active manager of this organization required");
            if (exception.getErrorCode() == 51813)
                throw failure(409, "EVENT_VERSION_CONFLICT", "Event has changed; reload before saving");
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
