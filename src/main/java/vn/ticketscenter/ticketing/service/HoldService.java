package vn.ticketscenter.ticketing.service;

import vn.ticketscenter.ticketing.cache.RedisActiveHoldCache;
import vn.ticketscenter.ticketing.cache.RedisHoldIdempotency;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.repository.HoldRepository;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class HoldService {
    private final HoldRepository repository;
    private final RedisActiveHoldCache activeCache;
    private final RedisHoldIdempotency receipts;

    public HoldService(HoldRepository repository, RedisActiveHoldCache activeCache, RedisHoldIdempotency receipts) {
        this.repository = Objects.requireNonNull(repository);
        this.activeCache = Objects.requireNonNull(activeCache);
        this.receipts = Objects.requireNonNull(receipts);
    }

    public HoldMutationReceipt mutate(HoldActor actor, HoldCommand command) {
        HoldMutationReceipt receipt = repository.mutate(actor, command);
        invalidate(receipt.changes());
        try {
            var response = new RedisHoldIdempotency.CompletedResponse(receipt.httpStatus(),
                    "application/json;charset=UTF-8", receipt.body());
            var state = receipts.acquire(actor.id(), command.key().toString(), command.fingerprint());
            if (state instanceof RedisHoldIdempotency.Acquired acquired)
                receipts.complete(acquired.claim(), response, RedisHoldIdempotency.MAX_RESULT_RETENTION);
            else
                receipts.recoverConfirmedResult(actor.id(), command.key().toString(), command.fingerprint(),
                        response, RedisHoldIdempotency.MAX_RESULT_RETENTION);
        } catch (RuntimeException redisUnavailable) {
        }
        return receipt;
    }

    public CurrentHold current(HoldActor actor) {
        var cached = activeCache.lookup(actor.id());
        CurrentHold current = repository.current(actor);
        if (!cached.projection().equals(current.projection())) {
            if (cached.projection().isPresent()) {
                activeCache.invalidateAfterCommit(actor.id());
                return current;
            }
            var token = cached.fillToken().orElse(null);
            current.projection().ifPresent(projection -> activeCache.fill(token, projection));
        }
        return current;
    }

    public String options(HoldActor actor, UUID eventId) { return repository.options(actor, eventId); }

    public int expire(int batchSize) {
        List<HoldMutationReceipt.Change> changes = repository.expire(batchSize);
        invalidate(changes);
        return changes.size();
    }

    private void invalidate(List<HoldMutationReceipt.Change> changes) {
        changes.stream().map(HoldMutationReceipt.Change::actorId).distinct().forEach(activeCache::invalidateAfterCommit);
    }
}
