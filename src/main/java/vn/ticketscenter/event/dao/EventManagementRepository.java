package vn.ticketscenter.event.dao;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.event.dto.EventCommand;

import java.sql.*;
import java.util.*;

public class EventManagementRepository {
    public record Actor(UUID id, int authVersion) {}

    public record Result(
            int status, String json, UUID organizationId, UUID eventId, String cleanupJson) {
        public Result(int status, String json, UUID organizationId, UUID eventId) {
            this(status, json, organizationId, eventId, null);
        }
    }

    public record Context(UUID organizationId, UUID eventId) {}

    public record CoverContext(
            UUID organizationId, String status, long version, String publicId, String coverUrl) {}

    private final EventTransactions transactions;

    public EventManagementRepository(EventTransactions transactions) {
        this.transactions = transactions;
    }

    private DatabasePrincipal principal(boolean admin) {
        return admin ? DatabasePrincipal.ADMIN : DatabasePrincipal.MANAGER;
    }

    public String read(
            Actor actor,
            boolean admin,
            String kind,
            UUID org,
            UUID event,
            UUID zone,
            String status,
            int page,
            int size) {
        return transactions.run(
                principal(admin),
                actor.id(),
                c ->
                        eventRead(
                                c, actor, admin, kind, org, event, zone, status, page, size,
                                "Missing Event projection", rows -> rows.getString(1)));
    }

    public Context authorize(Actor actor, EventCommand command) {
        boolean review = command.scope().equals("EVENT_REVIEW");
        return transactions.run(
                principal(review),
                actor.id(),
                c ->
                        eventRead(
                                c, actor, review, "AUTHORIZE",
                                command.organizationId(), command.eventId(), command.zoneId(), null, 1, 20,
                                "Missing Event authorization",
                                rows -> new Context(uuid(rows.getString(1)), uuid(rows.getString(2)))));
    }

    public CoverContext coverContext(Actor actor, UUID event) {
        return transactions.run(
                DatabasePrincipal.MANAGER,
                actor.id(),
                c ->
                        eventRead(
                                c, actor, false, "COVER_CONTEXT", null, event, null, null, 1, 20,
                                "Missing Event cover context",
                                rows ->
                                        new CoverContext(
                                                uuid(rows.getString(1)),
                                                rows.getString(2),
                                                rows.getLong(3),
                                                rows.getString(4),
                                                rows.getString(5))));
    }

    /** usp_EventRead answers every read kind with one row; a missing row is a database failure. */
    private static <T> T eventRead(
            Connection c,
            Actor actor,
            boolean admin,
            String kind,
            UUID org,
            UUID event,
            UUID zone,
            String status,
            int page,
            int size,
            String missing,
            SqlCall.RowMapper<T> mapper)
            throws SQLException {
        try (var q = c.prepareCall("{call dbo.usp_EventRead(?,?,?,?,?,?,?,?,?,?)}")) {
            q.setQueryTimeout(10);
            q.setString(1, actor.id().toString());
            q.setInt(2, actor.authVersion());
            q.setBoolean(3, admin);
            q.setString(4, kind);
            id(q, 5, org);
            id(q, 6, event);
            id(q, 7, zone);
            q.setString(8, status);
            q.setInt(9, page);
            q.setInt(10, size);
            try (var rows = q.executeQuery()) {
                if (!rows.next()) throw new SQLException(missing);
                return mapper.map(rows);
            }
        }
    }

    public Optional<Result> receipt(Actor actor, String scope, String key, String hash) {
        return transactions.run(
                principal(scope.equals("EVENT_REVIEW")),
                actor.id(),
                c -> {
                    try (var q = c.prepareCall("{call dbo.usp_EventReceipt(?,?,?,?,?)}")) {
                        q.setQueryTimeout(10);
                        q.setString(1, actor.id().toString());
                        q.setInt(2, actor.authVersion());
                        q.setString(3, scope);
                        q.setString(4, key);
                        q.setString(5, hash);
                        try (var rows = q.executeQuery()) {
                            return rows.next() ? Optional.of(result(rows)) : Optional.empty();
                        }
                    }
                });
    }

    public Result command(Actor actor, String key, String hash, EventCommand command) {
        return transactions.run(
                principal(command.scope().equals("EVENT_REVIEW")),
                actor.id(),
                c -> {
                    try (var q =
                            c.prepareCall("{call dbo.usp_EventCommand(?,?,?,?,?,?,?,?,?,?)}")) {
                        q.setQueryTimeout(15);
                        q.setString(1, actor.id().toString());
                        q.setInt(2, actor.authVersion());
                        q.setString(3, command.scope());
                        q.setString(4, key);
                        q.setString(5, hash);
                        q.setString(6, command.operation());
                        id(q, 7, command.organizationId());
                        id(q, 8, command.eventId());
                        id(q, 9, command.zoneId());
                        q.setString(10, command.json());
                        boolean more = q.execute();
                        while (true) {
                            if (more)
                                try (var rows = q.getResultSet()) {
                                    // Publish's canonical SP emits an intermediate result set;
                                    // consume it, then the durable receipt.
                                    if (rows.getMetaData().getColumnCount() >= 4
                                            && rows.getMetaData()
                                                    .getColumnLabel(1)
                                                    .equalsIgnoreCase("status_code")
                                            && rows.next()) return result(rows);
                                }
                            else if (q.getUpdateCount() == -1) break;
                            more = q.getMoreResults();
                        }
                        throw new SQLException("Missing Event command receipt");
                    }
                });
    }

    private static Result result(ResultSet rows) throws SQLException {
        return new Result(
                rows.getInt(1),
                rows.getString(2),
                uuid(rows.getString(3)),
                uuid(rows.getString(4)),
                rows.getMetaData().getColumnCount() > 4 ? rows.getString(5) : null);
    }

    private static UUID uuid(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private static void id(PreparedStatement q, int index, UUID id) throws SQLException {
        q.setString(index, id == null ? null : id.toString());
    }
}
