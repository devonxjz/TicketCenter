package vn.ticketscenter.config.worker;

import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.worker.RefundLeaseImpl;
import vn.ticketscenter.fulfillment.refund.worker.RefundProcessingWorker;
import vn.ticketscenter.fulfillment.refund.worker.RefundReconciliationJob;
import vn.ticketscenter.fulfillment.refund.worker.RefundWorkerRepository;
import vn.ticketscenter.payment.integration.RefundGateway;

import java.util.Objects;

public final class RefundJob {
    private final RefundProcessingWorker processing;
    private final RefundReconciliationJob reconciliation;

    public RefundJob(TransactionManager transactions, RefundGateway gateway, int batchSize) {
        this(
                new RefundProcessingWorker(
                        new RefundWorkerRepository(transactions),
                        gateway,
                        new RefundLeaseImpl(),
                        batchSize));
    }

    public RefundJob(RefundProcessingWorker processing) {
        this.processing = Objects.requireNonNull(processing);
        this.reconciliation = new RefundReconciliationJob(processing);
    }

    public int runOnce() {
        return processing.runOnce() + reconciliation.runOnce();
    }
}
