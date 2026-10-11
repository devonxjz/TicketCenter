package vn.ticketscenter.order.service;

import vn.ticketscenter.order.cache.RedisOrderCache;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.repository.OrderRepository;

import java.util.Objects;
import java.util.UUID;

public final class OrderService {
    private final OrderRepository repository;
    private final RedisOrderCache cache;
    public OrderService(OrderRepository repository, RedisOrderCache cache) {
        this.repository = Objects.requireNonNull(repository); this.cache = Objects.requireNonNull(cache);
    }
    public OrderMutationReceipt mutate(OrderActor actor, OrderCommand command) {
        OrderMutationReceipt receipt = repository.mutate(actor, command);
        cache.invalidateAfterCommit(actor.id(), receipt.holdId(), receipt.orderId());
        cache.saveReceiptAfterCommit(actor, command, receipt);
        return receipt;
    }
    public CurrentCheckout checkout(OrderActor actor, UUID holdId) {
        var lookup = cache.lookup(actor.id(), holdId);
        CurrentCheckout current = repository.checkout(actor, holdId);
        if (lookup.order().isPresent() && !lookup.order().equals(current.order())) {
            cache.invalidateAfterCommit(actor.id(), holdId, lookup.order().get().snapshot().orderId());
            return current;
        }
        current.order().ifPresent(order -> {
            if (order.canEditCoupon()) cache.fill(lookup.token().orElse(null), order);
            else cache.invalidateAfterCommit(actor.id(), holdId, order.snapshot().orderId());
        });
        return current;
    }
    public String eligibility(OrderActor actor, UUID orderId, String couponCode) { return repository.eligibility(actor, orderId, couponCode); }
}
