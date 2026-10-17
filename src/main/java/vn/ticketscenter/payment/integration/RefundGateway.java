package vn.ticketscenter.payment.integration;

import vn.ticketscenter.config.error.UpstreamException;
import vn.ticketscenter.fulfillment.refund.worker.RefundTransferRequest;
import vn.ticketscenter.fulfillment.refund.worker.RefundTransferResult;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundGateway {
    enum Status {
        SUCCEEDED,
        FAILED,
        UNKNOWN,
        PENDING
    }

    record Result(Status status, String providerReference) {}

    class Timeout extends UpstreamException {
        public Timeout() {
            super("REFUND_GATEWAY_TIMEOUT", "Refund gateway timed out");
        }
    }

    Result submit(UUID refundId, BigDecimal amount) throws Timeout;

    Result query(String providerReference) throws Timeout;

    default RefundTransferResult executeRefund(RefundTransferRequest request) throws Timeout {
        return RefundTransferResult.from(submit(request.attemptId(), request.amount()));
    }

    /** Lookup by attempt ID is needed when submit's response was lost. */
    default RefundTransferResult queryRefundStatus(String providerReference, UUID attemptId)
            throws Timeout {
        return RefundTransferResult.from(
                providerReference == null
                        ? new Result(Status.UNKNOWN, null)
                        : query(providerReference));
    }
}
