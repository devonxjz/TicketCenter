package vn.ticketscenter.identity.dao;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Runs one identity write procedure (migration 072) on the entity manager's connection, so it joins the
 * caller's transaction. The persistence context is cleared afterwards: entities read before the call are
 * stale once the procedure has written.
 */
final class IdentityProcedures {

    interface Binder {
        void bind(CallableStatement call) throws SQLException;
    }

    interface RowReader<T> {
        T read(ResultSet row) throws SQLException;
    }

    private IdentityProcedures() {}

    /** Calls a procedure that returns no rows. */
    static void execute(EntityManager em, String sql, Binder binder) {
        query(em, sql, binder, null);
    }

    /** Calls a procedure and reads its first row; the reader is not called when it returns none. */
    static <T> T query(EntityManager em, String sql, Binder binder, RowReader<T> reader) {
        Objects.requireNonNull(em, "EntityManager must not be null");
        try {
            return em.unwrap(Session.class).doReturningWork(connection -> {
                try (CallableStatement call = connection.prepareCall(sql)) {
                    binder.bind(call);
                    if (reader == null) {
                        call.execute();
                        return null;
                    }
                    try (ResultSet row = call.executeQuery()) {
                        return row.next() ? reader.read(row) : null;
                    }
                } catch (SQLException failure) {
                    throw translate(failure);
                }
            });
        } finally {
            em.clear();
        }
    }

    /** Maps the 072 THROW numbers and unique-key violations onto the identity errors the API reports. */
    static RuntimeException translate(SQLException failure) {
        for (SQLException error = failure; error != null; error = error.getNextException()) {
            switch (error.getErrorCode()) {
                case 52900:
                    return new IdentityException(
                            IdentityError.EMAIL_ALREADY_EXISTS, "EMAIL_ALREADY_EXISTS: Email đã được sử dụng");
                case 52901:
                    return new IdentityException(
                            IdentityError.USERNAME_ALREADY_EXISTS,
                            "USERNAME_ALREADY_EXISTS: Tên đăng nhập đã được sử dụng");
                case 2601:
                case 2627:
                    return new IdentityException(
                            IdentityError.IDENTITY_CONFLICT,
                            "IDENTITY_CONFLICT: Email hoặc tên đăng nhập đã tồn tại trong hệ thống",
                            failure);
                default:
                    break;
            }
        }
        return new IllegalStateException("Identity procedure failed", failure);
    }
}
