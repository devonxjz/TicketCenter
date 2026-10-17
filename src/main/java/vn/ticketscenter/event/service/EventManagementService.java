package vn.ticketscenter.event.service;

import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.dto.EventResponseValues;
import vn.ticketscenter.storage.StoredAsset;
import vn.ticketscenter.event.dao.EventManagementRepository;
import vn.ticketscenter.event.dao.EventManagementRepository.*;
import java.util.*;

public final class EventManagementService {
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(EventManagementService.class.getName());
    private final EventManagementRepository repository;
    private final EventIdempotencyStore idempotency;
    private final EventCacheInvalidator invalidation;
    private final EventCoverService.Orphans orphans;

    public EventManagementService(
            EventManagementRepository repository,
            EventIdempotencyStore idempotency,
            EventCacheInvalidator invalidation) {
        this(repository, idempotency, invalidation, (event, asset) -> {});
    }

    public EventManagementService(
            EventManagementRepository repository,
            EventIdempotencyStore idempotency,
            EventCacheInvalidator invalidation,
            EventCoverService.Orphans orphans) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.invalidation = invalidation;
        this.orphans = Objects.requireNonNull(orphans);
    }

    public String read(
            Actor actor,
            boolean admin,
            String kind,
            UUID org,
            UUID event,
            UUID zone,
            String status,
            int page,
            int size) {
        return repository.read(actor, admin, kind, org, event, zone, status, page, size);
    }

    public Result mutate(Actor actor, String key, EventCommand command) {
        validateKey(key);
        String hash = command.hash();
        var old = repository.receipt(actor, command.scope(), key, hash);
        if (old.isPresent()) {
            committed(old.get());
            return old.get();
        }
        repository.authorize(actor, command);
        var lease = idempotency.acquire(redisKey(actor, command.scope(), key), hash);
        try {
            var result = repository.command(actor, key, hash, command);
            committed(result);
            try {
                idempotency.completed(lease, hash, result);
            } catch (RuntimeException ignored) {
            }
            return result;
        } catch (RuntimeException e) {
            try {
                idempotency.release(lease);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void committed(Result result) {
        invalidate(result);
        if (result.cleanupJson() == null || result.eventId() == null) return;
        try {
            String id = EventResponseValues.string(result.cleanupJson(), "publicId").orElse(null);
            String url =
                    EventResponseValues.string(result.cleanupJson(), "coverImageUrl").orElse(null);
            if (id != null) orphans.record(result.eventId(), new StoredAsset(url, id));
        } catch (RuntimeException ignored) {
            LOGGER.warning(
                    "Event deletion cleanup journal pending; durable SQL metadata retained for reconciliation.");
        }
    }

    public void invalidate(Result result) {
        try {
            invalidation.invalidateCommitted(result.eventId(), result.organizationId());
        } catch (RuntimeException ignored) {
            /* A committed result is never reported as a failed write. */
        }
    }

    public static void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
            throw EventProblem.invalid("Cần Idempotency-Key hợp lệ (tối đa 128 ký tự).");
    }

    public static String redisKey(Actor actor, String scope, String key) {
        return (scope.equals("EVENT_REVIEW") ? "tc:idem:event-review:" : "tc:idem:event:")
                + actor.id()
                + ":"
                + key;
    }
}
