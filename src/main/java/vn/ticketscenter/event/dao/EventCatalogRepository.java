package vn.ticketscenter.event.dao;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.dto.EventDtos;
import vn.ticketscenter.event.dto.EventFilter;

import java.sql.*;
import java.time.*;
import java.util.*;

/** Public view is the visibility boundary for every event/zone/seat query. */
public class EventCatalogRepository {
    private final TransactionManager transactions;
    private static final String COLUMNS =
            "e.event_id,e.category_id,e.title,%s AS description,e.cover_image_url,e.venue_name,e.venue_address,"
                    + "e.sale_start,e.sale_end,e.start_time,e.end_time,e.minimum_price,e.category_name,e.organizer_name,e.maximum_price,e.is_on_sale";
    private static final String PUBLIC_PROJECTION = " FROM dbo.vw_PublicEventCatalog e";

    public EventCatalogRepository(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public EventDtos.Page events(EventFilter filter) {
        return read(
                connection -> {
                    String where = " WHERE " + RealEventVisibility.predicate("e");
                    List<Object> parameters = new ArrayList<>();
                    if (!filter.keyword().isEmpty()) {
                        where +=
                                " AND (e.title LIKE ? ESCAPE '\\' OR e.venue_name LIKE ? ESCAPE '\\' OR e.venue_address LIKE ? ESCAPE '\\')";
                        parameters.add(filter.keywordPattern());
                        parameters.add(filter.keywordPattern());
                        parameters.add(filter.keywordPattern());
                    }
                    if (filter.categoryId() != null) {
                        where += " AND category_id=?";
                        parameters.add(filter.categoryId());
                    }
                    if (filter.from() != null) {
                        where += " AND start_time>=?";
                        parameters.add(filter.from());
                    }
                    if (filter.to() != null) {
                        where += " AND start_time<?";
                        parameters.add(filter.to());
                    }
                    long total;
                    try (var query =
                            prepare(
                                    connection,
                                    "SELECT COUNT_BIG(*)" + PUBLIC_PROJECTION + where)) {
                        bind(query, parameters);
                        try (var rows = query.executeQuery()) {
                            rows.next();
                            total = rows.getLong(1);
                        }
                    }
                    List<EventDtos.Event> events = new ArrayList<>();
                    try (var query =
                            prepare(
                                    connection,
                                    "SELECT "
                                            + COLUMNS.formatted("CAST(NULL AS nvarchar(max))")
                                            + PUBLIC_PROJECTION
                                            + where
                                            + " ORDER BY "
                                            + filter.orderBy()
                                            + " OFFSET ? ROWS FETCH NEXT ? ROWS ONLY")) {
                        bind(query, parameters);
                        query.setInt(parameters.size() + 1, filter.offset());
                        query.setInt(parameters.size() + 2, filter.pageSize());
                        try (var rows = query.executeQuery()) {
                            while (rows.next()) events.add(event(rows));
                        }
                    }
                    return new EventDtos.Page(events, total);
                });
    }

    public Optional<EventDtos.Event> event(UUID id) {
        return read(
                connection -> {
                    try (var query =
                            prepare(
                                    connection,
                                    "SELECT "
                                            + COLUMNS.formatted("e.description")
                                            + PUBLIC_PROJECTION
                                            + " WHERE e.event_id=? AND "
                                            + RealEventVisibility.predicate("e"))) {
                        query.setString(1, id.toString());
                        try (var rows = query.executeQuery()) {
                            return rows.next() ? Optional.of(event(rows)) : Optional.empty();
                        }
                    }
                });
    }

    /** Parent and child read share a statement; hidden/missing parent is distinct from an empty collection. */
    public Optional<List<EventDtos.Zone>> zones(UUID eventId) {
        return read(
                connection -> {
                    try (var query =
                            prepare(
                                    connection,
                                    "SELECT z.zone_id,z.event_id,z.name,z.type,z.price,z.capacity,z.held_quantity,z.sold_quantity,z.available_quantity"
                                            + " FROM dbo.vw_PublicEvents e LEFT JOIN dbo.vw_ZoneInventory z ON z.event_id=e.event_id"
                                            + " WHERE e.event_id=? AND "
                                            + RealEventVisibility.predicate("e")
                                            + " ORDER BY z.name,z.zone_id")) {
                        query.setString(1, eventId.toString());
                        try (var rows = query.executeQuery()) {
                            if (!rows.next()) return Optional.empty();
                            List<EventDtos.Zone> zones = new ArrayList<>();
                            do {
                                if (rows.getString(1) != null)
                                    zones.add(
                                            new EventDtos.Zone(
                                                    id(rows, 1),
                                                    id(rows, 2),
                                                    rows.getString(3),
                                                    rows.getString(4),
                                                    rows.getBigDecimal(5),
                                                    rows.getLong(6),
                                                    rows.getLong(7),
                                                    rows.getLong(8),
                                                    rows.getLong(9)));
                            } while (rows.next());
                            return Optional.of(List.copyOf(zones));
                        }
                    }
                });
    }

    public Optional<List<EventDtos.Seat>> seats(UUID zoneId) {
        return read(
                connection -> {
                    try (var query =
                            prepare(
                                    connection,
                                    "SELECT s.id,s.zone_id,s.row_name,s.seat_number,s.label,s.status"
                                            + " FROM dbo.vw_ZoneInventory z JOIN dbo.vw_PublicEvents e ON e.event_id=z.event_id"
                                            + " LEFT JOIN dbo.vw_PublicEventSeats s ON s.zone_id=z.zone_id WHERE z.zone_id=? AND "
                                            + RealEventVisibility.predicate("e")
                                            + " ORDER BY LEN(s.row_name),s.row_name,s.seat_number,s.id")) {
                        query.setString(1, zoneId.toString());
                        try (var rows = query.executeQuery()) {
                            if (!rows.next()) return Optional.empty();
                            List<EventDtos.Seat> seats = new ArrayList<>();
                            do {
                                if (rows.getString(1) != null)
                                    seats.add(
                                            new EventDtos.Seat(
                                                    id(rows, 1),
                                                    id(rows, 2),
                                                    rows.getString(3),
                                                    rows.getInt(4),
                                                    rows.getString(5),
                                                    rows.getString(6)));
                            } while (rows.next());
                            return Optional.of(List.copyOf(seats));
                        }
                    }
                });
    }

    public List<EventDtos.Category> categories() {
        return read(
                connection -> {
                    try (var query =
                                    prepare(
                                            connection,
                                            "SELECT id,name,slug FROM dbo.vw_EventCategories ORDER BY name,id");
                            var rows = query.executeQuery()) {
                        List<EventDtos.Category> items = new ArrayList<>();
                        while (rows.next())
                            items.add(
                                    new EventDtos.Category(
                                            id(rows, 1), rows.getString(2), rows.getString(3)));
                        return List.copyOf(items);
                    }
                });
    }

    private <T> T read(SqlRead<T> work) {
        return transactions.execute(
                DatabasePrincipal.BUYER,
                em -> em.unwrap(Session.class).doReturningWork(work::apply));
    }

    @FunctionalInterface
    private interface SqlRead<T> {
        T apply(Connection connection) throws SQLException;
    }

    private static PreparedStatement prepare(Connection connection, String sql)
            throws SQLException {
        var query = connection.prepareStatement(sql);
        query.setQueryTimeout(5);
        return query;
    }

    private static void bind(PreparedStatement query, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            if (value instanceof Instant instant)
                query.setObject(i + 1, LocalDateTime.ofInstant(instant, ZoneOffset.UTC));
            else query.setString(i + 1, value.toString());
        }
    }

    private static UUID id(ResultSet rows, int column) throws SQLException {
        return UUID.fromString(rows.getString(column));
    }

    private static Instant instant(ResultSet rows, int column) throws SQLException {
        if (rows.getMetaData().getColumnType(column) == -155)
            return rows.getObject(column, OffsetDateTime.class).toInstant();
        return rows.getObject(column, LocalDateTime.class).toInstant(ZoneOffset.UTC);
    }

    private static EventDtos.Event event(ResultSet rows) throws SQLException {
        return new EventDtos.Event(
                id(rows, 1),
                id(rows, 2),
                rows.getString(3),
                rows.getString(4),
                rows.getString(5),
                rows.getString(6),
                rows.getString(7),
                instant(rows, 8),
                instant(rows, 9),
                instant(rows, 10),
                instant(rows, 11),
                rows.getBigDecimal(12),
                rows.getString(13),
                rows.getString(14),
                rows.getBigDecimal(15),
                rows.getBoolean(16));
    }
}
