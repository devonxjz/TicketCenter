package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface AdminRefundDecisionService {
    AdminRefundPageDto list(UserPrincipal actor, String status, int page, int size);

    AdminRefundDetailDto detail(UserPrincipal actor, UUID id);

    AdminRefundMutationDto decide(
            UserPrincipal actor, UUID id, AdminRefundDecisionRequestDto request, UUID key);

    AdminRefundMutationDto retry(UserPrincipal actor, UUID id, AdminRefundRetryRequestDto request);
}
