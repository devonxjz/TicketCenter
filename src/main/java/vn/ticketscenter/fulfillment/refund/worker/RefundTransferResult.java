package vn.ticketscenter.fulfillment.refund.worker;

import java.util.Objects;
import vn.ticketscenter.payment.integration.RefundGateway;

/**
 * Validated provider observation; missing/invalid final references are never treated as success.
 */
public record RefundTransferResult(RefundGateway.Status status, String providerReference) {
    public RefundTransferResult {
        Objects.requireNonNull(status);
        if (status == RefundGateway.Status.PENDING) status = RefundGateway.Status.UNKNOWN;
        if (providerReference != null && providerReference.length() > 200)
            throw new IllegalArgumentException("Provider reference too long");
        if (providerReference != null && providerReference.isBlank()) providerReference = null;
        if ((status == RefundGateway.Status.SUCCEEDED || status == RefundGateway.Status.FAILED)
                && providerReference == null)
            throw new IllegalArgumentException("Final result requires provider reference");
    }

    public static RefundTransferResult from(RefundGateway.Result result) {
        return new RefundTransferResult(result.status(), result.providerReference());
    }

    public RefundGateway.Result gatewayResult() {
        return new RefundGateway.Result(status, providerReference);
    }
}
