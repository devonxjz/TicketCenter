package vn.ticketscenter.fulfillment.checkin.dao;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.CheckInException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only projections: no entity graphs or financial columns are selected. */
public class CheckInEventRepository {
    public record EventInfo(
            UUID eventId,
            UUID organizationId,
            String title,
            String venue,
            Instant startTime,
            Instant endTime,
            String status,
            long issued,
            long checkedIn,
            Instant checkInOpenAt) {
        public EventInfo(
                UUID eventId,
                UUID organizationId,
                String title,
                String venue,
                Instant startTime,
                Instant endTime,
                String status,
                long issued,
                long checkedIn) {
            this(eventId, organizationId, title, venue, startTime, endTime, status, issued, checkedIn, null);
        }

        /** Per-event override when set, otherwise one hour before the start. */
        public Instant windowStart() {
            return checkInOpenAt != null ? checkInOpenAt : startTime.minusSeconds(3600);
        }

        public boolean isOpen(Instant now) {
            return "PUBLISHED".equals(status)
                    && !now.isBefore(windowStart())
                    && now.isBefore(endTime);
        }
    }

    private final TransactionManager transactions;

    public CheckInEventRepository(TransactionManager transactions) {
        this.transactions = transactions;
    }

    /** Active MANAGER / CHECK_IN_STAFF memberships of active users; the rule lives in the view. */
    private static final String MEMBERSHIP =
            """
            FROM dbo.vw_CheckInStaffOrganizations m
            WHERE m.user_id=?
            """;

    private static final String EVENT_COLUMNS =
            """
            SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status,e.checkin_open_at
            FROM dbo.vw_CheckInEvents e
            """;

    public List<UUID> authorizedOrganizations(UUID actorId) {
        return read(
                connection -> {
                    try (PreparedStatement query =
                            prepare(connection, "SELECT m.organization_id " + MEMBERSHIP)) {
                        query.setString(1, actorId.toString());
                        try (ResultSet rows = query.executeQuery()) {
                            List<UUID> ids = new ArrayList<>();
                            while (rows.next()) ids.add(UUID.fromString(rows.getString(1)));
                            ids.sort(Comparator.comparing(UUID::toString));
                            return List.copyOf(ids);
                        }
                    }
                });
    }

    /** Always read current membership and schedule, even on a cache hit. */
    public List<EventInfo> accessibleEvents(UUID actorId, UUID organizationId, Instant now) {
        return read(
                connection -> {
                    String sql =
                            EVENT_COLUMNS
                                    + " WHERE e.organization_id=? AND e.end_time>=?"
                                    + " AND e.status IN ('PUBLISHED','CANCELLED') AND EXISTS (SELECT 1 "
                                    + MEMBERSHIP
                                    + " AND m.organization_id=e.organization_id) ORDER BY e.start_time,e.id";
                    try (PreparedStatement query = prepare(connection, sql)) {
                        query.setString(1, organizationId.toString());
                        utc(query, 2, now.truncatedTo(ChronoUnit.DAYS));
                        query.setString(3, actorId.toString());
                        try (ResultSet rows = query.executeQuery()) {
                            List<EventInfo> events = new ArrayList<>();
                            while (rows.next()) events.add(event(rows));
                            return List.copyOf(events);
                        }
                    }
                });
    }

    public EventInfo accessibleEvent(UUID actorId, UUID eventId) {
        return read(
                connection -> {
                    try (PreparedStatement query =
                            prepare(connection, EVENT_COLUMNS + " WHERE e.id=?")) {
                        query.setString(1, eventId.toString());
                        try (ResultSet rows = query.executeQuery()) {
                            if (!rows.next())
                                throw new CheckInException(
                                        404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại.");
                            EventInfo event = event(rows);
                            try (PreparedStatement access =
                                    prepare(
                                            connection,
                                            "SELECT 1 "
                                                    + MEMBERSHIP
                                                    + " AND m.organization_id=?")) {
                                access.setString(1, actorId.toString());
                                access.setString(2, event.organizationId().toString());
                                try (ResultSet membership = access.executeQuery()) {
                                    if (!membership.next())
                                        throw new CheckInException(
                                                403,
                                                "CHECK_IN_FORBIDDEN",
                                                "Bạn không có quyền soát vé tại sự kiện này.");
                                }
                            }
                            return event;
                        }
                    }
                });
    }

    public EventInfo withTicketCounts(EventInfo event) {
        return read(
                connection -> {
                    // Count issued tickets (including subsequently used/refunded tickets), never
                    // their prices.
                    String sql =
                            "SELECT issued_count,checked_in_count FROM dbo.fn_CheckInEventTicketCounts(?)";
                    try (PreparedStatement query = prepare(connection, sql)) {
                        query.setString(1, event.eventId().toString());
                        try (ResultSet rows = query.executeQuery()) {
                            rows.next();
                            return new EventInfo(
                                    event.eventId(),
                                    event.organizationId(),
                                    event.title(),
                                    event.venue(),
                                    event.startTime(),
                                    event.endTime(),
                                    event.status(),
                                    rows.getLong(1),
                                    rows.getLong(2),
                                    event.checkInOpenAt());
                        }
                    }
                });
    }

    /** One indexed guard on a warm cache, instead of separate Event and membership projections. */
    public boolean isCachedWindowCurrent(UUID actorId, UUID eventId, EventInfo cached) {
        return read(
                connection -> {
                    String sql =
                            "SELECT CASE WHEN EXISTS (SELECT 1 "
                                    + MEMBERSHIP
                                    + " AND m.organization_id=e.organization_id) THEN 1 ELSE 0 END,"
                                    + " CASE WHEN e.organization_id=? AND e.start_time=? AND e.end_time=? AND e.status=?"
                                    + " AND ISNULL(e.checkin_open_at,'1900-01-01')=? THEN 1 ELSE 0 END"
                                    + " FROM dbo.vw_CheckInEvents e WHERE e.id=?";
                    try (PreparedStatement query = prepare(connection, sql)) {
                        query.setString(1, actorId.toString());
                        query.setString(2, cached.organizationId().toString());
                        utc(query, 3, cached.startTime());
                        utc(query, 4, cached.endTime());
                        query.setString(5, cached.status());
                        utc(
                                query,
                                6,
                                cached.checkInOpenAt() == null
                                        ? Instant.parse("1900-01-01T00:00:00Z")
                                        : cached.checkInOpenAt());
                        query.setString(7, eventId.toString());
                        try (ResultSet rows = query.executeQuery()) {
                            if (!rows.next())
                                throw new CheckInException(
                                        404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại.");
                            if (!rows.getBoolean(1))
                                throw new CheckInException(
                                        403,
                                        "CHECK_IN_FORBIDDEN",
                                        "Bạn không có quyền soát vé tại sự kiện này.");
                            return cached.eventId().equals(eventId) && rows.getBoolean(2);
                        }
                    }
                });
    }

    public boolean isCheckInOpen(UUID eventId, Instant now) {
        return read(
                connection -> {
                    try (PreparedStatement query =
                            prepare(connection, "SELECT dbo.fn_event_is_check_in_open(?,?)")) {
                        query.setString(1, eventId.toString());
                        utc(query, 2, now);
                        try (ResultSet rows = query.executeQuery()) {
                            if (!rows.next())
                                throw new SQLException("Missing check-in window result");
                            return rows.getBoolean(1);
                        }
                    }
                });
    }

    private <T> T read(SqlRead<T> work) {
        return transactions.execute(
                DatabasePrincipal.CHECK_IN,
                entityManager -> entityManager.unwrap(Session.class).doReturningWork(work::apply));
    }

    @FunctionalInterface
    private interface SqlRead<T> {
        T apply(Connection connection) throws SQLException;
    }

    private static PreparedStatement prepare(Connection connection, String sql)
            throws SQLException {
        PreparedStatement query = connection.prepareStatement(sql);
        query.setQueryTimeout(5);
        return query;
    }

    private static EventInfo event(ResultSet row) throws SQLException {
        return new EventInfo(
                UUID.fromString(row.getString(1)),
                UUID.fromString(row.getString(2)),
                row.getString(3),
                row.getString(4),
                instant(row, 5),
                instant(row, 6),
                row.getString(7),
                0,
                0,
                row.getObject(8) == null ? null : instant(row, 8));
    }

    private static Instant instant(ResultSet row, int index) throws SQLException {
        // SQL Server datetimeoffset must retain its offset; LocalDateTime is converted
        // by the driver into the JVM's timezone and can shift the window by seven hours.
        if (row.getMetaData().getColumnType(index) == -155) // SQL Server DATETIMEOFFSET JDBC type
        return row.getObject(index, OffsetDateTime.class).toInstant();
        return row.getObject(index, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private static void utc(PreparedStatement query, int index, Instant instant)
            throws SQLException {
        query.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
}
