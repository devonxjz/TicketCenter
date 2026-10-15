package vn.ticketscenter.payment.integration;

import vn.ticketscenter.config.error.UpstreamException;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundGateway {
    enum Status { SUCCEEDED, FAILED, UNKNOWN, PENDING }
    record Result(Status status, String providerReference) {}
    class Timeout extends UpstreamException {
        public Timeout() { super("REFUND_GATEWAY_TIMEOUT", "Refund gateway timed out"); }
    }

    Result submit(UUID refundId, BigDecimal amount) throws Timeout;
    Result query(String providerReference) throws Timeout;

    default vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult executeRefund(vn.ticketscenter.fulfillment.refund.worker.RefundTransferRequest request) throws Timeout {
        return vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult.from(submit(request.attemptId(), request.amount()));
    }
    /** Lookup by attempt ID is needed when submit's response was lost. */
    default vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult queryRefundStatus(String providerReference, UUID attemptId) throws Timeout {
        return vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult.from(providerReference == null ? new Result(Status.UNKNOWN, null) : query(providerReference));
    }
}
