package vn.ticketscenter.fulfillment.refund.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundPageDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto;
import vn.ticketscenter.identity.security.UserPrincipal;

public class RefundRequestService {
    private static final RefundRequestCache.Lease NO_LEASE = () -> {};

    private final RefundRequestStore store;
    private final RefundRequestCache cache;

    public RefundRequestService(RefundRequestStore store, RefundRequestCache cache) {
        this.store = Objects.requireNonNull(store);
        this.cache = Objects.requireNonNull(cache);
    }

    public List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID orderId) {
        authorize(actor);
        if (orderId == null) throw RefundException.invalid();
        return store.tickets(actor, orderId);
    }

    public List<RefundRequestStore.OrderOption> orders(UserPrincipal actor) {
        authorize(actor);
        return store.orders(actor);
    }

    public RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key) {
        authorize(actor);
        if (request == null || key == null) throw RefundException.invalid();

        RefundRequestCache.Lease lease = lockTickets(request.ticketIds());
        try {
            RefundSummaryDto result = store.create(actor, request, key);
            rememberResult(actor, key, request, result);
            return result;
        } finally {
            releaseQuietly(lease);
        }
    }

    public RefundPageDto list(UserPrincipal actor, int page, int size) {
        authorize(actor);
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw RefundException.invalid();
        return store.list(actor, page, size);
    }

    public RefundDetailDto detail(UserPrincipal actor, UUID refundId) {
        authorize(actor);
        if (refundId == null) throw RefundException.invalid();
        return store.detail(actor, refundId);
    }

    private void authorize(UserPrincipal actor) {
        if (actor == null || !store.active(actor)) {
            throw new RefundException(
                    401, "SESSION_EXPIRED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn.");
        }
    }

    /** Redis only narrows races; SQL still checks the durable key when the lock is unavailable. */
    private RefundRequestCache.Lease lockTickets(List<UUID> ticketIds) {
        try {
            return cache.lock(ticketIds);
        } catch (RuntimeException unavailable) {
            return NO_LEASE;
        }
    }

    /** The request is already committed in SQL, so a cache failure must not surface. */
    private void rememberResult(
            UserPrincipal actor, UUID key, CreateRefundRequestDto request, RefundSummaryDto result) {
        try {
            cache.committed(actor.actorId(), key, request.ticketIds(), result);
        } catch (RuntimeException ignored) {
        }
    }

    private static void releaseQuietly(RefundRequestCache.Lease lease) {
        try {
            lease.close();
        } catch (RuntimeException ignored) {
        }
    }
}
