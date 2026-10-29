package vn.ticketscenter.config.worker;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import vn.ticketscenter.payment.integration.PayosGateway;
import vn.ticketscenter.payment.dao.PaymentRepository.ReconcileCandidate;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Settles payments stuck in PENDING/UNKNOWN by asking payOS for the truth. The gateway call happens
 * outside any database transaction; one failing payment never blocks the rest of the batch.
 */
public final class PaymentReconciliationJob {
    private static final Logger LOGGER = Logger.getLogger(PaymentReconciliationJob.class.getName());

    public interface Source {
        List<ReconcileCandidate> findBatch(int limit);
    }

    public interface Gateway {
        PayosGateway.Result query(ReconcileCandidate candidate);
    }

    public interface Applier {
        void apply(ReconcileCandidate candidate, String status, String providerReference);
    }

    public interface OrderLocks {
        AutoCloseable acquire(UUID orderId) throws Exception;
    }

    /** Rows read per run: wide enough that payments still waiting on a retry cannot hide newer ones. */
    private static final int FETCH_FACTOR = 25;

    private static final long FIRST_RETRY_NANOS = TimeUnit.MINUTES.toNanos(2);
    private static final long MAX_RETRY_NANOS = TimeUnit.MINUTES.toNanos(30);

    private record Retry(int attempts, long notBeforeNanos) {}

    private final Source source;
    private final Gateway gateway;
    private final Applier applier;
    private final OrderLocks locks;
    private final int batchSize;
    private final LongSupplier nanoClock;

    /** In-memory only: a restart simply asks the gateway again. */
    private final Map<UUID, Retry> retries = new HashMap<>();

    public PaymentReconciliationJob(
            Source source, Gateway gateway, Applier applier, OrderLocks locks, int batchSize) {
        this(source, gateway, applier, locks, batchSize, System::nanoTime);
    }

    PaymentReconciliationJob(
            Source source,
            Gateway gateway,
            Applier applier,
            OrderLocks locks,
            int batchSize,
            LongSupplier nanoClock) {
        this.source = source;
        this.gateway = gateway;
        this.applier = applier;
        this.locks = locks;
        this.batchSize = batchSize;
        this.nanoClock = nanoClock;
    }

    /** @return number of payments settled to a final status in this run */
    public int runOnce() {
        int settled = 0;
        int attempted = 0;
        List<ReconcileCandidate> candidates = source.findBatch(batchSize * FETCH_FACTOR);
        Set<UUID> open = new HashSet<>();
        for (ReconcileCandidate candidate : candidates) {
            open.add(candidate.paymentId());
        }
        retries.keySet().retainAll(open);
        for (ReconcileCandidate candidate : candidates) {
            if (Thread.currentThread().isInterrupted() || attempted >= batchSize) {
                break;
            }
            Retry retry = retries.get(candidate.paymentId());
            if (retry != null && nanoClock.getAsLong() - retry.notBeforeNanos() < 0) {
                continue;
            }
            attempted++;
            try {
                PayosGateway.Result result = gateway.query(candidate);
                if (result == null || result.outcome() == PayosGateway.Outcome.UNKNOWN) {
                    defer(candidate, retry);
                    continue;
                }
                try (AutoCloseable lock = locks.acquire(candidate.orderId())) {
                    applier.apply(candidate, result.outcome().name(), result.providerReference());
                    retries.remove(candidate.paymentId());
                    settled++;
                }
            } catch (Exception failure) {
                defer(candidate, retry);
                LOGGER.log(
                        Level.WARNING,
                        "Payment reconciliation failed for payment "
                                + candidate.paymentId()
                                + "; will retry",
                        failure);
            }
        }
        return settled;
    }

    /** Unresolved payments back off (2, 4, 8 … 30 minutes) so they stop occupying every batch. */
    private void defer(ReconcileCandidate candidate, Retry previous) {
        int attempts = previous == null ? 1 : previous.attempts() + 1;
        long delay = Math.min(MAX_RETRY_NANOS, FIRST_RETRY_NANOS << Math.min(attempts - 1, 4));
        retries.put(candidate.paymentId(), new Retry(attempts, nanoClock.getAsLong() + delay));
    }
}
