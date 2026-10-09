package vn.ticketscenter.fulfillment.checkin.repository;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.CheckInException;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Read-only projections: no entity graphs or financial columns are selected. */
public class CheckInEventRepository {
    public record EventInfo(UUID eventId, UUID organizationId, String title, String venue,
            Instant startTime, Instant endTime, String status, long issued, long checkedIn) {
        public Instant windowStart() { return startTime.minusSeconds(3600); }
        public boolean isOpen(Instant now) {
            return "PUBLISHED".equals(status) && !now.isBefore(windowStart()) && now.isBefore(endTime);
        }
    }

    private final TransactionManager transactions;
    public CheckInEventRepository(TransactionManager transactions) { this.transactions = transactions; }

    private static final String MEMBERSHIP = """
            FROM dbo.tc_organization_memberships m
            JOIN dbo.tc_users u ON u.id=m.user_id
            WHERE m.user_id=? AND m.active=1 AND u.status='ACTIVE'
              AND m.role IN ('MANAGER','CHECK_IN_STAFF')
            """;
    private static final String EVENT_COLUMNS = """
            SELECT e.id,e.organization_id,e.title,e.venue_name,e.start_time,e.end_time,e.status
            FROM dbo.tc_events e
            """;

    public List<UUID> authorizedOrganizations(UUID actorId) {
        return read(connection -> {
            try (PreparedStatement query = prepare(connection, "SELECT m.organization_id " + MEMBERSHIP)) {
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
        return read(connection -> {
            String sql = EVENT_COLUMNS + " WHERE e.organization_id=? AND e.end_time>=?"
                    + " AND e.status IN ('PUBLISHED','CANCELLED') AND EXISTS (SELECT 1 "
                    + MEMBERSHIP + " AND m.organization_id=e.organization_id) ORDER BY e.start_time,e.id";
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
        return read(connection -> {
            try (PreparedStatement query = prepare(connection, EVENT_COLUMNS + " WHERE e.id=?")) {
                query.setString(1, eventId.toString());
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new CheckInException(404, "EVENT_NOT_FOUND", "Sự kiện không tồn tại.");
                    EventInfo event = event(rows);
                    try (PreparedStatement access = prepare(connection, "SELECT 1 " + MEMBERSHIP + " AND m.organization_id=?")) {
                        access.setString(1, actorId.toString());
                        access.setString(2, event.organizationId().toString());
                        try (ResultSet membership = access.executeQuery()) {
                            if (!membership.next()) throw new CheckInException(403, "CHECK_IN_FORBIDDEN", "Bạn không có quyền soát vé tại sự kiện này.");
                        }
                    }
                    return event;
                }
            }
        });
    }

    public EventInfo withTicketCounts(EventInfo event) {
        return read(connection -> {
            // Count issued tickets (including subsequently used/refunded tickets), never their prices.
            String sql = """
                    SELECT COUNT_BIG(*),COALESCE(SUM(CASE WHEN t.status IN ('CHECKED_IN','USED')
                        THEN CAST(1 AS bigint) ELSE CAST(0 AS bigint) END),0)
                    FROM dbo.tc_tickets t JOIN dbo.tc_order_items i ON i.id=t.order_item_id
                    JOIN dbo.tc_orders o ON o.id=i.order_id WHERE o.event_id=?
                    """;
            try (PreparedStatement query = prepare(connection, sql)) {
                query.setString(1, event.eventId().toString());
                try (ResultSet rows = query.executeQuery()) {
                    rows.next();
                    return new EventInfo(event.eventId(), event.organizationId(), event.title(), event.venue(),
                            event.startTime(), event.endTime(), event.status(), rows.getLong(1), rows.getLong(2));
                }
            }
        });
    }

    public boolean isCheckInOpen(UUID eventId, Instant now) {
        return read(connection -> {
            try (PreparedStatement query = prepare(connection, "SELECT dbo.fn_event_is_check_in_open(?,?)")) {
                query.setString(1, eventId.toString());
                utc(query, 2, now);
                try (ResultSet rows = query.executeQuery()) {
                    if (!rows.next()) throw new SQLException("Missing check-in window result");
                    return rows.getBoolean(1);
                }
            }
        });
    }

    private <T> T read(SqlRead<T> work) {
        return transactions.execute(DatabasePrincipal.CHECK_IN,
                entityManager -> entityManager.unwrap(Session.class).doReturningWork(work::apply));
    }
    @FunctionalInterface private interface SqlRead<T> { T apply(Connection connection) throws SQLException; }
    private static PreparedStatement prepare(Connection connection, String sql) throws SQLException {
        PreparedStatement query = connection.prepareStatement(sql);
        query.setQueryTimeout(5);
        return query;
    }
    private static EventInfo event(ResultSet row) throws SQLException {
        return new EventInfo(UUID.fromString(row.getString(1)), UUID.fromString(row.getString(2)),
                row.getString(3), row.getString(4), instant(row, 5), instant(row, 6), row.getString(7), 0, 0);
    }
    private static Instant instant(ResultSet row, int index) throws SQLException {
        return row.getObject(index, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }
    private static void utc(PreparedStatement query, int index, Instant instant) throws SQLException {
        query.setObject(index, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
}
