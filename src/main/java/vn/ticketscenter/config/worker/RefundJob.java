package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.repository.RefundRepository;
import vn.ticketscenter.payment.integration.RefundGateway;

import java.util.Objects;

public final class RefundJob {
    private final TransactionManager transactions;
    private final RefundRepository repository;
    private final RefundGateway gateway;
    private final int batchSize;

    public RefundJob(TransactionManager transactions, RefundRepository repository,
                     RefundGateway gateway, int batchSize) {
        this.transactions = Objects.requireNonNull(transactions);
        this.repository = Objects.requireNonNull(repository);
        this.gateway = Objects.requireNonNull(gateway);
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be positive");
        this.batchSize = batchSize;
    }

    public int runOnce() {
        var work = transactions.execute(DatabasePrincipal.WORKER,
                entityManager -> repository.findWork(entityManager, batchSize));
        int applied = 0;
        for (var item : work) {
            try {
                RefundGateway.Result result = item.providerReference() == null
                        ? gateway.submit(item.refundId(), item.amount())
                        : gateway.query(item.providerReference());
                transactions.execute(DatabasePrincipal.WORKER, entityManager -> {
                    repository.apply(entityManager, item.refundId(), result);
                    return null;
                });
                applied++;
            } catch (RefundGateway.Timeout ignored) {
                // The durable provider result is reconciled by the next run.
            }
        }
        return applied;
    }
}
