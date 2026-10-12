package vn.ticketscenter.fulfillment.refund.dto;

import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

/** The server generates attemptId; the client supplies only an idempotency key. */
public record AdminRefundRetryRequestDto(UUID idempotencyKey) {
    public AdminRefundRetryRequestDto { if (idempotencyKey == null) throw RefundException.invalid(); }
}
