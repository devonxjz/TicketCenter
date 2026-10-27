package vn.ticketscenter.event.cancellation.service;

import vn.ticketscenter.event.cancellation.cache.CancellationProgressCache;
import vn.ticketscenter.event.cancellation.dao.EventCancellationStore;
import java.util.Objects;
import java.util.UUID;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.event.cancellation.dto.EventCancelResponseDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.UserPrincipal;

public final class EventCancellationServiceImpl implements EventCancellationService {
    private final EventCancellationStore store;
    private final CancellationProgressCache cache;

    public EventCancellationServiceImpl(
            EventCancellationStore store, CancellationProgressCache cache) {
        this.store = Objects.requireNonNull(store);
        this.cache = Objects.requireNonNull(cache);
    }

    @Override
    public EventCancelResponseDto cancel(
            UserPrincipal actor, UUID id, EventCancelRequestDto request, UUID key) {
        authorize(actor);
        if (id == null || request == null || key == null) throw invalid();
        var result = store.cancel(actor, id, request, key);
        try {
            cache.cancelled(result);
        } catch (RuntimeException ignored) {
            // Cancellation has committed; cache availability cannot change its result.
        }
        return new EventCancelResponseDto(
                result.eventId(), result.cancelledAt(), result.totalOrders());
    }

    @Override
    public String progress(UserPrincipal actor, UUID id) {
        authorize(actor);
        if (id == null) throw invalid();

        // Check current SQL permissions before serving cached progress.
        var cached = cache.get(id);
        if (cached.isPresent()) return cached.get();

        var result = store.progress(actor, id);
        try {
            cache.publish(result);
        } catch (RuntimeException ignored) {
            // SQL progress remains available when Redis is down.
        }
        return result.json();
    }

    private void authorize(UserPrincipal actor) {
        if (actor == null) throw new RefundException(401, "UNAUTHENTICATED", "Bạn cần đăng nhập.");
        if (!actor.isAdmin())
            throw new RefundException(403, "ADMIN_REQUIRED", "Chỉ quản trị viên được hủy sự kiện.");
        store.requireAdmin(actor);
    }

    private static RefundException invalid() {
        return new RefundException(
                400, "CANCELLATION_INVALID", "Thông tin hủy sự kiện không hợp lệ.");
    }
}
