package vn.ticketscenter.event.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;
import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.SqlCall;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.event.cancellation.dto.CancellationProgressDto;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.event.cancellation.dao.EventCancellationStore;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

import static vn.ticketscenter.config.persistence.SqlCall.uuid;

public class EventCancellationRepository implements EventCancellationStore {
    public record Work(UUID eventId, UUID orderId) {}

    private final TransactionManager transactions;
    private final DataSource supplied;

    public EventCancellationRepository() {
        transactions = null;
        supplied = null;
    }

    public EventCancellationRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
        supplied = null;
    }

    public EventCancellationRepository(DataSource source) {
        supplied = Objects.requireNonNull(source);
        transactions = null;
    }

    @Override
    public void requireAdmin(UserPrincipal actor) {
        admin(
                connection -> {
                    authority(connection, actor);
                    return null;
                });
    }

    @Override
    public CancellationProgressDto cancel(
            UserPrincipal actor, UUID id, EventCancelRequestDto request, UUID key) {
        if (transactions != null) {
            try {
                return transactions.execute(
                        DatabasePrincipal.ADMIN,
                        em -> {
                            var procedure = em.createStoredProcedureQuery("dbo.sp_event_cancel");
                            for (String name :
                                    List.of(
                                            "event_id",
                                            "admin_user_id",
                                            "cancel_reason",
                                            "idempotency_key")) {
                                procedure.registerStoredProcedureParameter(
                                        name, String.class, ParameterMode.IN);
                            }
                            procedure
                                    .registerStoredProcedureParameter(
                                            "auth_version", Integer.class, ParameterMode.IN)
                                    .setParameter("event_id", id.toString())
                                    .setParameter("admin_user_id", actor.actorId().toString())
                                    .setParameter("cancel_reason", request.reason())
                                    .setParameter("idempotency_key", key.toString())
                                    .setParameter("auth_version", actor.authVersion());
                            consume(procedure);
                            em.clear();
                            return em.unwrap(Session.class)
                                    .doReturningWork(
                                            connection -> {
                                                try (var query =
                                                                query(
                                                                        connection,
                                                                        "SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=?",
                                                                        id);
                                                        var result = query.executeQuery()) {
                                                    if (!result.next())
                                                        throw new SQLException(
                                                                "Missing cancellation projection");
                                                    return projection(result);
                                                }
                                            });
                        });
            } catch (Exception failure) {
                throw translated(failure);
            }
        }
        return admin(
                connection -> {
                    try (var query =
                            query(
                                    connection,
                                    "EXEC dbo.sp_event_cancel ?,?,?,?,?",
                                    id,
                                    actor.actorId(),
                                    request.reason(),
                                    key,
                                    actor.authVersion())) {
                        boolean hasResult = query.execute();
                        while (!hasResult && query.getUpdateCount() != -1)
                            hasResult = query.getMoreResults();
                        if (!hasResult) throw new SQLException("Missing cancellation projection");
                        try (var result = query.getResultSet()) {
                            if (!result.next()) throw new SQLException("Missing cancellation row");
                            return projection(result);
                        }
                    }
                });
    }

    @Override
    public CancellationProgressDto progress(UserPrincipal actor, UUID id) {
        return admin(
                connection -> {
                    authority(connection, actor);
                    try (var query =
                                    query(
                                            connection,
                                            "SELECT * FROM dbo.v_admin_cancellation_progress WHERE event_id=?",
                                            id);
                            var result = query.executeQuery()) {
                        if (!result.next()) {
                            throw new RefundException(
                                    404, "CANCELLATION_NOT_FOUND", "Sự kiện chưa có tác vụ hủy.");
                        }
                        return projection(result);
                    }
                });
    }

    public List<Object> findWork(EntityManager entityManager, int batchSize) {
        if (batchSize < 1 || batchSize > 1000)
            throw new IllegalArgumentException("Invalid cancellation batch size");
        return entityManager
                .unwrap(Session.class)
                .doReturningWork(
                        connection -> {
                            var work = new ArrayList<Object>();
                            String sql =
                                    "SELECT TOP(?) event_id,order_id FROM dbo.v_worker_cancellation_orders ORDER BY cancelled_at,event_id,order_id";
                            try (var query = query(connection, sql, batchSize);
                                    var result = query.executeQuery()) {
                                while (result.next())
                                    work.add(
                                            new Work(
                                                    uuid(result, "event_id"),
                                                    uuid(result, "order_id")));
                            }
                            return work;
                        });
    }

    public void process(EntityManager entityManager, Object item) {
        var work = (Work) item;
        var procedure =
                entityManager
                        .createStoredProcedureQuery("dbo.sp_refund_adopt_event_cancellation")
                        .registerStoredProcedureParameter(
                                "event_id", String.class, ParameterMode.IN)
                        .registerStoredProcedureParameter(
                                "order_id", String.class, ParameterMode.IN)
                        .setParameter("event_id", work.eventId().toString())
                        .setParameter("order_id", work.orderId().toString());
        consume(procedure);
        entityManager.clear();
    }

    public void completeFinishedEvents(EntityManager entityManager) {
        entityManager.createStoredProcedureQuery("dbo.sp_event_cancellation_finish").execute();
        entityManager.clear();
    }

    private <T> T admin(SqlCall.SqlWork<T> work) {
        try {
            if (transactions != null) {
                return SqlCall.inSession(transactions, DatabasePrincipal.ADMIN, work);
            }
            try (var connection = supplied.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    var result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (RuntimeException | SQLException failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        } catch (Exception failure) {
            throw translated(failure);
        }
    }

    private static RuntimeException translated(Exception failure) {
        if (failure instanceof RefundException error) return error;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof SQLException sql)) continue;
            int status =
                    switch (sql.getErrorCode()) {
                        case 54200 -> 400;
                        case 54201 -> 401;
                        case 54203 -> 403;
                        case 54204 -> 404;
                        case 54209 -> 409;
                        default -> 503;
                    };
            String code =
                    switch (status) {
                        case 400 -> "EVENT_CANNOT_BE_CANCELLED";
                        case 401 -> "SESSION_EXPIRED";
                        case 403 -> "ADMIN_REQUIRED";
                        case 404 -> "CANCELLATION_NOT_FOUND";
                        case 409 -> "CANCELLATION_CONFLICT";
                        default -> "DATABASE_UNAVAILABLE";
                    };
            return new RefundException(
                    status,
                    code,
                    "Không thể xử lý hủy sự kiện. Hãy kiểm tra quyền, thời gian và trạng thái sự kiện.");
        }
        return new RefundException(
                503, "DATABASE_UNAVAILABLE", "Hệ thống hủy sự kiện tạm thời không khả dụng.");
    }

    private static PreparedStatement query(Connection connection, String sql, Object... args)
            throws SQLException {
        var query = connection.prepareStatement(sql);
        query.setQueryTimeout(20);
        for (int i = 0; i < args.length; i++) {
            query.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
        }
        return query;
    }

    private static void authority(Connection connection, UserPrincipal actor) throws SQLException {
        try (var query =
                        query(
                                connection,
                                "SELECT status,auth_version,is_admin FROM dbo.v_admin_refund_actor WHERE id=?",
                                actor.actorId());
                var result = query.executeQuery()) {
            if (!result.next()
                    || !"ACTIVE".equals(result.getString(1))
                    || result.getInt(2) != actor.authVersion()) {
                throw new RefundException(401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
            }
            if (!result.getBoolean(3))
                throw new RefundException(
                        403, "ADMIN_REQUIRED", "Bạn không còn quyền quản trị viên.");
        }
    }

    private static Instant instant(ResultSet result, String name) throws SQLException {
        var value = result.getTimestamp(name, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
        return value.toInstant();
    }

    private static CancellationProgressDto projection(ResultSet result) throws SQLException {
        return new CancellationProgressDto(
                uuid(result, "event_id"),
                uuid(result, "organization_id"),
                result.getString("status"),
                result.getString("reason"),
                instant(result, "cancelled_at"),
                result.getLong("total_orders"),
                result.getLong("processed_orders"),
                uuid(result, "last_processed_order_id"),
                instant(result, "updated_at"),
                result.getLong("successful_refunds"),
                result.getLong("failed_refunds"),
                result.getLong("unknown_refunds"),
                result.getLong("pending_refunds"),
                result.getLong("used_ticket_exceptions"));
    }

    private static void consume(StoredProcedureQuery procedure) {
        boolean hasResult = procedure.execute();
        // SP17 calls SP10; consume every result so later SQL errors roll back before commit.
        while (true) {
            if (hasResult) procedure.getResultList();
            else if (procedure.getUpdateCount() == -1) break;
            hasResult = procedure.hasMoreResults();
        }
    }
}
