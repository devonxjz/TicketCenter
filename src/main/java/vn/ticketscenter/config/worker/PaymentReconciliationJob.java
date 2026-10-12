package vn.ticketscenter.config.worker;

import vn.ticketscenter.payment.integration.VnpayQueryClient;
import vn.ticketscenter.payment.repository.PaymentRepository.ReconcileCandidate;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Settles payments stuck in PENDING/UNKNOWN by asking VNPAY for the truth. The gateway call happens
 * outside any database transaction; one failing payment never blocks the rest of the batch.
 */
public final class PaymentReconciliationJob {
    private static final Logger LOGGER = Logger.getLogger(PaymentReconciliationJob.class.getName());

    public interface Source { List<ReconcileCandidate> findBatch(int limit); }
    public interface Gateway { VnpayQueryClient.Result query(ReconcileCandidate candidate); }
    public interface Applier { void apply(ReconcileCandidate candidate, String status, String providerReference); }
    public interface OrderLocks { AutoCloseable acquire(java.util.UUID orderId) throws Exception; }

    private final Source source;
    private final Gateway gateway;
    private final Applier applier;
    private final OrderLocks locks;
    private final int batchSize;

    public PaymentReconciliationJob(Source source, Gateway gateway, Applier applier, OrderLocks locks, int batchSize) {
        this.source = source;
        this.gateway = gateway;
        this.applier = applier;
        this.locks = locks;
        this.batchSize = batchSize;
    }

    /** @return number of payments settled to a final status in this run */
    public int runOnce() {
        int settled = 0;
        for (ReconcileCandidate candidate : source.findBatch(batchSize)) {
            if (Thread.currentThread().isInterrupted()) break;
            try {
                VnpayQueryClient.Result result = gateway.query(candidate);
                if (result == null || result.outcome() == VnpayQueryClient.Outcome.UNKNOWN) continue;
                try (AutoCloseable lock = locks.acquire(candidate.orderId())) {
                    applier.apply(candidate, result.outcome().name(), result.providerReference());
                    settled++;
                }
            } catch (Exception failure) {
                LOGGER.log(Level.WARNING, "Payment reconciliation failed for payment " + candidate.paymentId() + "; will retry", failure);
            }
        }
        return settled;
    }
}
