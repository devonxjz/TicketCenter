package vn.ticketscenter.fulfillment.refund.worker;

import java.util.*;
import java.util.logging.Logger;
import vn.ticketscenter.fulfillment.refund.cache.RefundLease;
import vn.ticketscenter.payment.integration.RefundGateway;

/** First dispatch submits; recovery queries the same attempt. */
public final class RefundProcessingWorker {
    private static final Logger LOG = Logger.getLogger(RefundProcessingWorker.class.getName());

    private final RefundWorkerStore store;
    private final RefundGateway gateway;
    private final RefundLease leases;
    private final int batchSize;

    public RefundProcessingWorker(
            RefundWorkerStore store, RefundGateway gateway, RefundLease leases, int batchSize) {
        this.store = Objects.requireNonNull(store);
        this.gateway = Objects.requireNonNull(gateway);
        this.leases = Objects.requireNonNull(leases);
        if (batchSize < 1 || batchSize > 1000)
            throw new IllegalArgumentException("Batch must be 1..1000");
        this.batchSize = batchSize;
    }

    public int runOnce() {
        return run("EXECUTE");
    }

    int run(String kind) {
        int applied = 0;
        for (var candidate : store.candidates(kind, batchSize)) {
            try {
                var lease = leases.acquire(candidate.refundId());
                if (lease.isEmpty()) continue;
                try (var handle = lease.get()) {
                    var claimed = store.claim(candidate, UUID.randomUUID());
                    if (claimed.isEmpty()) continue;
                    var claim = claimed.get();
                    var outcome = transfer(claim);
                    store.record(claim, outcome.result().gatewayResult(), outcome.error());
                    applied++;
                }
            } catch (RuntimeException failure) {
                LOG.warning(
                        "Refund attempt remains durable for reconciliation: "
                                + candidate.attemptId());
            }
        }
        return applied;
    }

    private GatewayOutcome transfer(RefundWorkerStore.Claim claim) {
        try {
            var result =
                    claim.kind().equals("EXECUTE")
                            ? gateway.executeRefund(claim.request())
                            : gateway.queryRefundStatus(
                                    claim.providerReference(), claim.request().attemptId());
            if (result == null || result.status() == null)
                throw new IllegalStateException("Missing gateway result");
            String error =
                    result.status() == RefundGateway.Status.FAILED ? "GATEWAY_DECLINED" : null;
            return new GatewayOutcome(result, error);
        } catch (RuntimeException uncertain) {
            return new GatewayOutcome(
                    new RefundTransferResult(
                            RefundGateway.Status.UNKNOWN, claim.providerReference()),
                    "GATEWAY_OUTCOME_UNKNOWN");
        }
    }

    private record GatewayOutcome(RefundTransferResult result, String error) {}
}
