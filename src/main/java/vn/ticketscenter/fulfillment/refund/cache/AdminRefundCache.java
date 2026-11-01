package vn.ticketscenter.fulfillment.refund.cache;

import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundMutationDto;

@FunctionalInterface
public interface AdminRefundCache {
    void committed(UUID adminId, UUID idempotencyKey, AdminRefundMutationDto result);
}
