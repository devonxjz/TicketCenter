package vn.ticketscenter.event.dao;

import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.event.service.EventProblem;
import java.sql.*;
import java.util.UUID;

/** Feature-owned JDBC work inside the existing JPA transaction boundary. */
public class EventTransactions {
    @FunctionalInterface
    public interface Work<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final TransactionManager transactions;

    public EventTransactions(TransactionManager transactions) {
        this.transactions = transactions;
    }

    public <T> T run(DatabasePrincipal principal, Work<T> work) {
        try {
            return SqlCall.inSession(transactions, principal, work::execute);
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    /** Scope RLS to the authenticated actor and restore a reused connection on every exit. */
    public <T> T run(DatabasePrincipal principal, UUID actor, Work<T> work) {
        return run(
                principal,
                connection -> {
                    Object previous;
                    try (PreparedStatement query =
                            connection.prepareStatement("SELECT SESSION_CONTEXT(N'actor_id')")) {
                        query.setQueryTimeout(10);
                        try (ResultSet rows = query.executeQuery()) {
                            rows.next();
                            previous = rows.getObject(1);
                        }
                    }
                    try {
                        setActor(connection, actor.toString());
                        return work.execute(connection);
                    } finally {
                        setActor(connection, previous);
                    }
                });
    }

    private static void setActor(Connection connection, Object actor) throws SQLException {
        try (CallableStatement query = connection.prepareCall("{call sys.sp_set_session_context(?,?)}")) {
            query.setQueryTimeout(10);
            query.setString(1, "actor_id");
            query.setObject(2, actor);
            query.execute();
        }
    }

    public static RuntimeException translate(Throwable cause) {
        for (Throwable e = cause; e != null; e = e.getCause()) {
            if (e instanceof EventProblem p) {
                return p;
            }
            if (e instanceof SQLException sql) {
                int code = sql.getErrorCode();
                if (code == 52001) {
                    return new EventProblem(
                            401, "SESSION_EXPIRED", "Phiên đăng nhập không còn hiệu lực.");
                }
                if (code == 52003 || code == 51520) {
                    return new EventProblem(
                            403, "EVENT_FORBIDDEN", "Bạn không có quyền quản lý sự kiện này.");
                }
                if (code == 52004 || code == 51521) {
                    return new EventProblem(
                            404, "EVENT_NOT_FOUND", "Không tìm thấy sự kiện hoặc khu vực.");
                }
                if (code == 52000
                        || code == 51524
                        || code == 51525
                        || code == 51526
                        || code == 51527
                        || code == 515) {
                    return EventProblem.invalid(
                            "Thông tin sự kiện, lịch, khu vực hoặc chính sách chưa hợp lệ.");
                }
                if (code == 52009
                        || code == 51522
                        || code == 51523
                        || code == 547
                        || code == 2601
                        || code == 2627
                        || code == 1205
                        || (code >= 51101 && code <= 51107)) {
                    return EventProblem.conflict(
                            "Trạng thái, dữ liệu tồn kho hoặc idempotency key bị xung đột.");
                }
            }
        }
        return EventProblem.unavailable();
    }
}
