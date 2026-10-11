package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.repository.AdminRefundStore;
import vn.ticketscenter.identity.security.UserPrincipal;

public final class AdminRefundDecisionService {
    private final AdminRefundStore store;
    private final AdminRefundCache cache;
    private static final Set<String> STATES=Set.of("REQUESTED","APPROVED","PROCESSING","COMPLETED","REJECTED","RETRYABLE","NEEDS_RECONCILIATION");
    public AdminRefundDecisionService(AdminRefundStore store,AdminRefundCache cache) { this.store=Objects.requireNonNull(store);this.cache=Objects.requireNonNull(cache); }
    private void authorize(UserPrincipal actor) {
        if(actor==null)throw new RefundException(401,"UNAUTHENTICATED","Bạn cần đăng nhập.");
        if(!actor.isAdmin())throw new RefundException(403,"ADMIN_REQUIRED","Chỉ quản trị viên được phép xử lý hoàn tiền.");
        store.requireAdmin(actor); // Current status/authVersion and ADMIN role are database truth.
    }
    public AdminRefundPageDto list(UserPrincipal actor,String status,int page,int size) {
        authorize(actor);
        if(page<1||page>100000||size<1||size>100)throw RefundException.invalid();
        if(status!=null&&status.isBlank())status=null;
        if("FAILED".equals(status))status="RETRYABLE"; // Issue vocabulary maps to the SPEC canonical state.
        if("UNKNOWN".equals(status))status="NEEDS_RECONCILIATION";
        if(status!=null&&!STATES.contains(status))throw RefundException.invalid();
        return store.list(status,page,size);
    }
    public AdminRefundDetailDto detail(UserPrincipal actor,UUID id) { authorize(actor);if(id==null)throw RefundException.invalid();return store.detail(id); }
    public AdminRefundMutationDto decide(UserPrincipal actor,UUID id,AdminRefundDecisionRequestDto request,UUID key) {
        authorize(actor);if(id==null||request==null||key==null)throw RefundException.invalid();
        return committed(actor,key,store.decide(actor,id,request,key));
    }
    public AdminRefundMutationDto retry(UserPrincipal actor,UUID id,AdminRefundRetryRequestDto request) {
        authorize(actor);if(id==null||request==null)throw RefundException.invalid();
        return committed(actor,request.idempotencyKey(),store.retry(actor,id,request.idempotencyKey()));
    }
    private AdminRefundMutationDto committed(UserPrincipal actor,UUID key,AdminRefundMutationDto result) {
        // Store returns after commit. Redis failure must not turn committed money intent into a failed request.
        try { cache.committed(actor.actorId(),key,result); } catch(RuntimeException ignored) { }
        return result;
    }
}
