package vn.ticketscenter.payment.integration;

import org.hibernate.Session;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import java.util.Optional;
import java.util.UUID;

public final class VnpayRefundContextRepository implements VnpaySandboxRefundGateway.ContextLoader {
    private final TransactionManager transactions;
    public VnpayRefundContextRepository(TransactionManager transactions) { this.transactions=transactions; }
    @Override public Optional<VnpaySandboxRefundGateway.Context> load(UUID attemptId) {
        return transactions.execute(DatabasePrincipal.WORKER, em -> em.unwrap(Session.class).doReturningWork(c -> {
            try (var q=c.prepareStatement("SELECT * FROM dbo.vw_VnpayRefundContext WHERE attempt_id=?")) {
                q.setString(1,attemptId.toString()); q.setQueryTimeout(15);
                try(var r=q.executeQuery()) {
                    if (!r.next()) return Optional.empty();
                    return Optional.of(new VnpaySandboxRefundGateway.Context(UUID.fromString(r.getString("refund_id")), attemptId,
                            r.getBigDecimal("refund_amount"), r.getBigDecimal("payment_amount"), r.getString("currency"),
                            r.getString("payment_status"), r.getString("txn_ref"), r.getString("payment_reference"),
                            r.getString("tmn_code"), r.getString("environment"), r.getString("transaction_date"),
                            r.getTimestamp("attempt_created_at", java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))).toInstant()));
                }
            }
        }));
    }
}
