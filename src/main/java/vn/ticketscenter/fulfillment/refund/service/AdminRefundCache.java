package vn.ticketscenter.fulfillment.refund.service;

import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;

@FunctionalInterface
public interface AdminRefundCache {
    void committed(UUID adminId, UUID idempotencyKey, AdminRefundMutationDto result);
}
