package vn.ticketscenter.event.cancellation.service;

import java.util.Objects;
import java.util.UUID;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** SQL owns current authority, durable receipts, state and the atomic approval/cancellation. */
public final class CancellationReviewService {
    @FunctionalInterface public interface Store {
        String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key);
    }
    private final Store store;
    private final java.util.function.Consumer<UUID> approved;
    public CancellationReviewService(Store store, java.util.function.Consumer<UUID> approved) {
        this.store=Objects.requireNonNull(store);this.approved=Objects.requireNonNull(approved);
    }
    public String execute(UserPrincipal actor, UUID eventId, String action, String reason, UUID key) {
        if(actor==null)throw new RefundException(401,"AUTH_REQUIRED","Cần đăng nhập.");
        if(eventId==null || !java.util.Set.of("READ","REQUEST","APPROVE","REJECT").contains(action))throw invalid();
        if(java.util.Set.of("APPROVE","REJECT").contains(action) && !actor.isAdmin())
            throw new RefundException(403,"ADMIN_REQUIRED","Chỉ quản trị viên được quyết định yêu cầu hủy.");
        if(!action.equals("READ") && key==null)throw invalid();
        if(action.equals("REQUEST") || action.equals("REJECT"))reason=new EventCancelRequestDto(reason).reason();
        else if(reason!=null)throw invalid();
        String result=store.execute(actor,eventId,action,reason,key);
        if(action.equals("APPROVE"))try{approved.accept(eventId);}catch(RuntimeException ignored){ }
        return result;
    }
    private static RefundException invalid(){return new RefundException(400,"CANCELLATION_INVALID","Thông tin hủy không hợp lệ.");}
}
