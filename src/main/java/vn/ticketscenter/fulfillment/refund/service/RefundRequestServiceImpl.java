package vn.ticketscenter.fulfillment.refund.service;

import java.time.Clock;
import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.identity.security.UserPrincipal;

public class RefundRequestServiceImpl implements RefundRequestService {
    private final RefundRequestStore store;
    private final RefundRequestCache cache;
    private final Clock clock;

    public RefundRequestServiceImpl(RefundRequestStore store, RefundRequestCache cache, Clock clock) {
        this.store = Objects.requireNonNull(store);
        this.cache = Objects.requireNonNull(cache);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID order) {
        authorize(actor);
        if (order == null) throw RefundException.invalid();
        return store.tickets(actor, order, clock.instant());
    }

    @Override
    public List<RefundRequestStore.OrderOption> orders(UserPrincipal actor) {
        authorize(actor);
        return store.orders(actor);
    }

    @Override
    public RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key) {
        authorize(actor);
        if (request == null || key == null) throw RefundException.invalid();

        // SQL checks the durable key even when Redis is unavailable.
        RefundRequestCache.Lease lease;
        try {
            lease = cache.lock(request.ticketIds());
        } catch (RuntimeException unavailable) {
            lease = () -> {};
        }
        try {
            var result = store.create(actor, request, key);
            try {
                cache.committed(actor.actorId(), key, request.ticketIds(), result);
            } catch (RuntimeException ignored) {
                // The store has committed; a cache failure cannot undo the request.
            }
            return result;
        } finally {
            try {
                lease.close();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public RefundPageDto list(UserPrincipal actor, int page, int size) {
        authorize(actor);
        if (page < 1 || page > 100000 || size < 1 || size > 100) throw RefundException.invalid();
        return store.list(actor, page, size);
    }

    @Override
    public RefundDetailDto detail(UserPrincipal actor, UUID id) {
        authorize(actor);
        if (id == null) throw RefundException.invalid();
        return store.detail(actor, id);
    }

    private void authorize(UserPrincipal actor) {
        if (actor == null || !store.active(actor)) {
            throw new RefundException(
                    401, "SESSION_EXPIRED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn.");
        }
    }
}
