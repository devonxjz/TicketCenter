package vn.ticketscenter.ticketing.dao;

import org.json.JSONArray;
import org.json.JSONObject;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.service.HoldException;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.event.dao.RealEventVisibility;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;

public final class HoldRepository {
    /** THROW numbers of the hold procedures. */
    private static final SqlCall.ErrorTable ERRORS =
            SqlCall.ErrorTable.of()
                    .on(51301, m -> error(401, "UNAUTHENTICATED", "A current session is required."))
                    .on(new int[] {51224, 51303}, m -> error(403, "FORBIDDEN", "An active verified account is required."))
                    .on(new int[] {51270, 51272, 51304}, m -> error(404, "NOT_FOUND", "Hold or Event was not found."))
                    .on(51308, m -> error(409, "IDEMPOTENCY_CONFLICT", "Use a new key for a changed command."))
                    .on(new int[] {51225, 51226, 51228, 51229, 51306}, m -> error(409, "HOLD_CONFLICT", "Refresh your Hold and available tickets."))
                    .on(new int[] {51220, 51221, 51222, 51223, 51227, 51300}, m -> error(400, "INVALID_REQUEST", "Invalid Hold selection."))
                    .fallback((code, message) -> HoldException.unavailable());

    private static HoldException error(int status, String code, String message) {
        return new HoldException(status, code, message);
    }

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
            for (HoldSelection selection : create.request().selections()) {
                selections.put(
                        new JSONObject()
                                .put("zoneId", selection.zoneId().toString())
                                .put(
                                        "seatId",
                                        selection.seatId() == null
                                                ? JSONObject.NULL
                                                : selection.seatId().toString())
                                .put("quantity", selection.quantity()));
            }
            return call(
                    buyer,
                    "EXEC dbo.usp_CreateHoldRequest ?,?,?,?,?,?",
                    rows -> receipt(rows, command),
                    actor.id(),
                    actor.authVersion(),
                    command.key(),
                    command.fingerprint(),
                    create.request().eventId(),
                    selections.toString());
        }
        HoldCommand.Cancel cancel = (HoldCommand.Cancel) command;
        return call(
                buyer,
                "EXEC dbo.usp_CancelHoldRequest ?,?,?,?,?",
                rows -> receipt(rows, command),
                actor.id(),
                actor.authVersion(),
                command.key(),
                command.fingerprint(),
                cancel.holdId());
    }

    public CurrentHold current(HoldActor actor) {
        CurrentHold current =
                call(
                        buyer,
                        "EXEC dbo.usp_ReadActiveHold ?,?",
                        rows -> {
                            String body = payload(rows);
                            JSONObject root = new JSONObject(body);
                            Instant serverTime = Instant.parse(root.getString("serverTime"));
                            if (root.isNull("hold")) {
                                return new CurrentHold(body, Optional.empty());
                            }
                            JSONObject hold = root.getJSONObject("hold");
                            if (!"ACTIVE".equals(hold.getString("status"))) {
                                throw HoldException.unavailable();
                            }
                            Instant expires = Instant.parse(hold.getString("expiresAt"));
                            if (!serverTime.isBefore(expires)) {
                                throw HoldException.unavailable();
                            }
                            List<ActiveHoldProjection.Item> items = new ArrayList<>();
                            JSONArray array = hold.getJSONArray("items");
                            for (int i = 0; i < array.length(); i++) {
                                JSONObject item = array.getJSONObject(i);
                                items.add(
                                        new ActiveHoldProjection.Item(
                                                id(item, "zoneId"),
                                                item.isNull("seatId") ? null : id(item, "seatId"),
                                                item.getInt("quantity"),
                                                new BigDecimal(item.getString("unitPrice"))));
                            }
                            return new CurrentHold(
                                    body,
                                    Optional.of(
                                            new ActiveHoldProjection(
                                                    id(hold, "id"),
                                                    actor.id(),
                                                    id(hold, "eventId"),
                                                    expires,
                                                    items)));
                        },
                        actor.id(),
                        actor.authVersion());
        // Release the procedure connection before acquiring one for the visibility read.
        if (current.projection().isPresent()) {
            requireVisibleEvent(current.projection().orElseThrow().eventId());
        }
        return current;
    }

    public String options(HoldActor actor, UUID eventId) {
        requireVisibleEvent(eventId);
        return call(
                buyer,
                "EXEC dbo.usp_ReadHoldOptions ?,?,?",
                rows -> {
                    String body = rows.getString("response_payload");
                    if (body == null) {
                        throw HoldException.unavailable();
                    }
                    new JSONObject(body);
                    return body;
                },
                actor.id(),
                actor.authVersion(),
                eventId);
    }

    private void requireVisibleEvent(UUID eventId) {
        if (ServerConfig.isDemoData()) {
            return;
        }
        String sql =
                "SELECT e.event_id FROM dbo.vw_PublicEvents e WHERE e.event_id=? AND "
                        + RealEventVisibility.predicate("e");
        boolean visible =
                SqlCall.onConnection(
                        buyer,
                        connection ->
                                SqlCall.callWithTimeout(connection, sql, 5, rows -> true, ERRORS, eventId)
                                        != null,
                        ERRORS);
        if (!visible) {
            throw new HoldException(404, "NOT_FOUND", "Hold or Event was not found.");
        }
    }

    public List<HoldMutationReceipt.Change> expire(int batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("Invalid expiry batch size");
        }
        return call(
                worker,
                "EXEC dbo.usp_ExpireHolds ?",
                rows -> changes(rows.getString("changes_payload")),
                batchSize);
    }

    private static HoldMutationReceipt receipt(ResultSet rows, HoldCommand command)
            throws SQLException {
        String body = payload(rows);
        JSONObject receipt = new JSONObject(body);
        String expected = command instanceof HoldCommand.Create ? "CREATED" : "CANCELLED";
        if (!expected.equals(receipt.getString("outcome"))) {
            throw HoldException.unavailable();
        }
        return new HoldMutationReceipt(
                command.httpStatus(),
                body,
                rows.getBoolean("replayed"),
                changes(rows.getString("changes_payload")));
    }

    private static String payload(ResultSet rows) throws SQLException {
        String body = rows.getString("response_payload");
        if (body == null || body.length() > 64 * 1024) {
            throw HoldException.unavailable();
        }
        new JSONObject(body);
        return body;
    }

    private static List<HoldMutationReceipt.Change> changes(String body) {
        if (body == null) {
            throw HoldException.unavailable();
        }
        JSONArray array = new JSONArray(body);
        List<HoldMutationReceipt.Change> changes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject change = array.getJSONObject(i);
            changes.add(
                    new HoldMutationReceipt.Change(
                            id(change, "actorId"),
                            id(change, "eventId"),
                            id(change, "holdId"),
                            change.getLong("version")));
        }
        return List.copyOf(changes);
    }

    private static UUID id(JSONObject value, String key) {
        return UUID.fromString(value.getString(key));
    }

    private static <T> T call(
            DataSource source, String sql, SqlCall.ResultSetExtractor<T> read, Object... args) {
        return SqlCall.callWithRetry(source, sql, read, ERRORS, HoldException::unavailable, args);
    }
}
