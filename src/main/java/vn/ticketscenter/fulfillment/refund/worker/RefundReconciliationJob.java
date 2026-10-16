package vn.ticketscenter.fulfillment.refund.worker;

import java.util.Objects;

/** SQL next_query_at enforces the 15 minute interval across nodes and restarts. */
public final class RefundReconciliationJob {
    private final RefundProcessingWorker worker;

    public RefundReconciliationJob(RefundProcessingWorker worker) {
        this.worker = Objects.requireNonNull(worker);
    }

    public int runOnce() {
        return worker.run("QUERY");
    }
}
