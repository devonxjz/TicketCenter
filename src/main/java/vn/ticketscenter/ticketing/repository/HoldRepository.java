package vn.ticketscenter.ticketing.repository;

import org.json.JSONArray;
import org.json.JSONObject;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.service.HoldException;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.event.repository.RealEventVisibility;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class HoldRepository {
    private final DataSource buyer;
    private final DataSource worker;

    public HoldRepository(DataSource buyer, DataSource worker) {
        this.buyer = Objects.requireNonNull(buyer);
        this.worker = Objects.requireNonNull(worker);
    }

    public HoldMutationReceipt mutate(HoldActor actor, HoldCommand command) {
        if (command instanceof HoldCommand.Create create) {
            requireVisibleEvent(create.request().eventId());
            JSONArray selections = new JSONArray();
            for (HoldSelection selection : create.request().selections())
                selections.put(new JSONObject().put("zoneId", selection.zoneId().toString())
                        .put("seatId", selection.seatId() == null ? JSONObject.NULL : selection.seatId().toString())
                        .put("quantity", selection.quantity()));
            return call(buyer, "EXEC dbo.usp_CreateHoldRequest ?,?,?,?,?,?", rows -> receipt(rows, command),
                    actor.id(), actor.authVersion(), command.key(), command.fingerprint(),
                    create.request().eventId(), selections.toString());
        }
        HoldCommand.Cancel cancel = (HoldCommand.Cancel) command;
        return call(buyer, "EXEC dbo.usp_CancelHoldRequest ?,?,?,?,?", rows -> receipt(rows, command),
                actor.id(), actor.authVersion(), command.key(), command.fingerprint(), cancel.holdId());
    }

    public CurrentHold current(HoldActor actor) {
        CurrentHold current = call(buyer, "EXEC dbo.usp_ReadActiveHold ?,?", rows -> {
            String body = payload(rows);
            JSONObject root = new JSONObject(body);
            Instant serverTime = Instant.parse(root.getString("serverTime"));
            if (root.isNull("hold")) return new CurrentHold(body, Optional.empty());
            JSONObject hold = root.getJSONObject("hold");
            if (!"ACTIVE".equals(hold.getString("status"))) throw HoldException.unavailable();
            Instant expires = Instant.parse(hold.getString("expiresAt"));
            if (!serverTime.isBefore(expires)) throw HoldException.unavailable();
            List<ActiveHoldProjection.Item> items = new ArrayList<>();
            JSONArray array = hold.getJSONArray("items");
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                items.add(new ActiveHoldProjection.Item(id(item, "zoneId"),
                        item.isNull("seatId") ? null : id(item, "seatId"), item.getInt("quantity"),
                        new BigDecimal(item.getString("unitPrice"))));
            }
            return new CurrentHold(body, Optional.of(new ActiveHoldProjection(id(hold, "id"),
                    actor.id(), id(hold, "eventId"), expires, items)));
        }, actor.id(), actor.authVersion());
        // Release the procedure connection before acquiring one for the visibility read.
        if (current.projection().isPresent()) requireVisibleEvent(current.projection().orElseThrow().eventId());
        return current;
    }

    public String options(HoldActor actor, UUID eventId) {
        requireVisibleEvent(eventId);
        return call(buyer, "EXEC dbo.usp_ReadHoldOptions ?,?,?", rows -> {
            String body = rows.getString("response_payload");
            if (body == null) throw HoldException.unavailable();
            new JSONObject(body);
            return body;
        },
                actor.id(), actor.authVersion(), eventId);
    }

    private void requireVisibleEvent(UUID eventId) {
        if (ServerConfig.isDemoData()) return;
        try (Connection connection = buyer.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT e.event_id FROM dbo.vw_PublicEvents e WHERE e.event_id=? AND "
                             + RealEventVisibility.predicate("e"))) {
            statement.setQueryTimeout(5);
            statement.setString(1, eventId.toString());
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) throw new HoldException(404, "NOT_FOUND", "Hold or Event was not found.");
            }
        } catch (SQLException failure) {
            throw HoldException.unavailable();
        }
    }

    public List<HoldMutationReceipt.Change> expire(int batchSize) {
        if (batchSize < 1 || batchSize > 100) throw new IllegalArgumentException("Invalid expiry batch size");
        return call(worker, "EXEC dbo.usp_ExpireHolds ?", rows -> changes(rows.getString("changes_payload")), batchSize);
    }

    private static HoldMutationReceipt receipt(ResultSet rows, HoldCommand command) throws SQLException {
        String body = payload(rows);
        JSONObject receipt = new JSONObject(body);
        String expected = command instanceof HoldCommand.Create ? "CREATED" : "CANCELLED";
        if (!expected.equals(receipt.getString("outcome"))) throw HoldException.unavailable();
        return new HoldMutationReceipt(command.httpStatus(), body, rows.getBoolean("replayed"),
                changes(rows.getString("changes_payload")));
    }

    private static String payload(ResultSet rows) throws SQLException {
        String body = rows.getString("response_payload");
        if (body == null || body.length() > 64 * 1024) throw HoldException.unavailable();
        new JSONObject(body);
        return body;
    }

    private static List<HoldMutationReceipt.Change> changes(String body) {
        if (body == null) throw HoldException.unavailable();
        JSONArray array = new JSONArray(body);
        List<HoldMutationReceipt.Change> changes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject change = array.getJSONObject(i);
            changes.add(new HoldMutationReceipt.Change(id(change, "actorId"), id(change, "eventId"),
                    id(change, "holdId"), change.getLong("version")));
        }
        return List.copyOf(changes);
    }

    private static UUID id(JSONObject value, String key) { return UUID.fromString(value.getString(key)); }

    @FunctionalInterface
    private interface Read<T> { T read(ResultSet rows) throws SQLException; }

    private static <T> T call(DataSource source, String sql, Read<T> read, Object... args) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try (Connection connection = source.getConnection()) {
                connection.setAutoCommit(true);
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(20);
                    for (int i = 0; i < args.length; i++)
                        statement.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
                    try (ResultSet rows = statement.executeQuery()) {
                        if (!rows.next()) throw HoldException.unavailable();
                        return read.read(rows);
                    }
                }
            } catch (SQLException failure) {
                HoldException domain = domainFailure(failure);
                if (domain != null) throw domain;
                if (attempt == 2 || !retryable(failure)) throw HoldException.unavailable();
            } catch (HoldException failure) {
                throw failure;
            } catch (RuntimeException invalidResponse) {
                throw HoldException.unavailable();
            }
        }
        throw HoldException.unavailable();
    }

    private static boolean retryable(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            int code = error.getErrorCode();
            if (code == 1205 || code == 1222 || code == 51309) return true;
            String state = error.getSQLState();
            if (state != null && (state.startsWith("08") || state.equals("HYT00") || state.equals("HYT01"))) return true;
        }
        return false;
    }

    private static HoldException domainFailure(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            HoldException mapped = switch (error.getErrorCode()) {
                case 51301 -> new HoldException(401, "UNAUTHENTICATED", "A current session is required.");
                case 51224, 51303 -> new HoldException(403, "FORBIDDEN", "An active verified account is required.");
                case 51270, 51272, 51304 -> new HoldException(404, "NOT_FOUND", "Hold or Event was not found.");
                case 51308 -> new HoldException(409, "IDEMPOTENCY_CONFLICT", "Use a new key for a changed command.");
                case 51225, 51226, 51228, 51229, 51306 -> new HoldException(409, "HOLD_CONFLICT", "Refresh your Hold and available tickets.");
                case 51220, 51221, 51222, 51223, 51227, 51300 -> new HoldException(400, "INVALID_REQUEST", "Invalid Hold selection.");
                default -> null;
            };
            if (mapped != null) return mapped;
        }
        return null;
    }
}
