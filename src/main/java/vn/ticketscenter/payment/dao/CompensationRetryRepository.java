package vn.ticketscenter.payment.dao;

import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Admin retry of a failed payment compensation through {@code usp_RetryPaymentCompensation}. */
public class CompensationRetryRepository implements CompensationRetryStore {

    private final TransactionManager transactions;

    public CompensationRetryRepository(TransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public UUID retry(UserPrincipal actor, UUID payment) {
        try {
            List<?> rows =
                    transactions.execute(
                            DatabasePrincipal.ADMIN,
                            em ->
                                    em.createNativeQuery(
                                                    "EXEC dbo.usp_RetryPaymentCompensation @payment_id=:payment,@actor_id=:actor,@auth_version=:version")
                                            .setParameter("payment", payment)
                                            .setParameter("actor", actor.actorId())
                                            .setParameter("version", actor.authVersion())
                                            .getResultList());
            return UUID.fromString(((Object[]) rows.get(0))[0].toString());
        } catch (RuntimeException failure) {
            throw translate(failure);
        }
    }

    public static RuntimeException translate(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (!(cause instanceof SQLException sql)) continue;
            for (SQLException error = sql; error != null; error = error.getNextException()) {
                switch (error.getErrorCode()) {
                    case 52810:
                        return new Rejected(
                                403, "ADMIN_REQUIRED", "Phiên quản trị không còn hiệu lực.");
                    case 52811:
                        return new Rejected(
                                404, "PAYMENT_NOT_FOUND", "Không tìm thấy thanh toán đã ghi nhận.");
                    case 52812:
                        return new Rejected(
                                409,
                                "COMPENSATION_NOT_RETRYABLE",
                                "Thanh toán này không có lần bù trừ thất bại để thử lại.");
                    default:
                        break;
                }
            }
        }
        return failure;
    }

    public static final class Rejected extends AppException {
        Rejected(int status, String code, String message) {
            super(status, code, message);
        }
    }
}
