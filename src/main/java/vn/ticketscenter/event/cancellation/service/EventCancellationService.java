package vn.ticketscenter.event.cancellation.service;

import java.util.*;
import vn.ticketscenter.event.cancellation.dto.*;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

public final class EventCancellationService {
    private final EventCancellationStore store;
    private final CancellationProgressCacheHelper cache;
    public EventCancellationService(EventCancellationStore store, CancellationProgressCacheHelper cache) {
        this.store = Objects.requireNonNull(store); this.cache = Objects.requireNonNull(cache);
    }
    private void authorize(UserPrincipal actor) {
        if (actor == null) throw new RefundException(401,"UNAUTHENTICATED","Bạn cần đăng nhập.");
        if (!actor.isAdmin()) throw new RefundException(403,"ADMIN_REQUIRED","Chỉ quản trị viên được hủy sự kiện.");
        store.requireAdmin(actor);
    }
    public EventCancelResponseDto cancel(UserPrincipal actor, UUID id, EventCancelRequestDto request, UUID key) {
        authorize(actor);
        if (id == null || request == null || key == null) throw invalid();
        var result = store.cancel(actor,id,request,key);
        // The store returns after commit. An unavailable cache cannot undo cancellation.
        try { cache.cancelled(result); } catch (RuntimeException ignored) { }
        return new EventCancelResponseDto(result.eventId(),result.cancelledAt(),result.totalOrders());
    }
    public String progress(UserPrincipal actor, UUID id) {
        authorize(actor);
        if (id == null) throw invalid();
        // Current SQL authority was checked above even on a warm cache.
        var cached=cache.get(id);
        if(cached.isPresent())return cached.get();
        var result = store.progress(actor,id);
        try { cache.publish(result); } catch (RuntimeException ignored) { }
        return result.json();
    }
    private static RefundException invalid() {return new RefundException(400,"CANCELLATION_INVALID","Thông tin hủy sự kiện không hợp lệ.");}
}
