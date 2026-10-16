package vn.ticketscenter.fulfillment.refund.dao;

import java.util.UUID;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.fulfillment.refund.dto.*;

public interface AdminRefundStore {
    void requireAdmin(UserPrincipal actor);
    AdminRefundPageDto list(String status,int page,int size);
    AdminRefundDetailDto detail(UUID id);
    AdminRefundMutationDto decide(UserPrincipal actor,UUID id,AdminRefundDecisionRequestDto request,UUID key);
    AdminRefundMutationDto retry(UserPrincipal actor,UUID id,UUID key);
}
