package vn.ticketscenter.fulfillment.refund.worker;

import java.util.*;
import vn.ticketscenter.payment.integration.RefundGateway;

public interface RefundWorkerStore {
    record Candidate(UUID refundId, UUID attemptId) {}

    record Claim(
            RefundTransferRequest request,
            String providerReference,
            String kind,
            UUID leaseToken) {}

    List<Candidate> candidates(String kind, int limit);

    Optional<Claim> claim(Candidate candidate, UUID token);

    void record(Claim claim, RefundGateway.Result result, String error);
}
