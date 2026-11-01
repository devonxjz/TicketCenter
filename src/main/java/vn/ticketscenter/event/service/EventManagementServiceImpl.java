package vn.ticketscenter.event.service;

import java.util.logging.Logger;
import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.EventCommand;
import vn.ticketscenter.event.dto.EventResponseValues;
import vn.ticketscenter.storage.StoredAsset;
import vn.ticketscenter.event.dao.EventManagementRepository;
import vn.ticketscenter.event.dao.EventManagementRepository.*;
import java.util.*;

public final class EventManagementServiceImpl implements EventManagementService {
    private static final Logger LOGGER =
            Logger.getLogger(EventManagementService.class.getName());
    private final EventManagementRepository repository;
    private final EventIdempotencyStore idempotency;
    private final EventCacheInvalidator invalidation;
    private final EventCoverService.Orphans orphans;

    public EventManagementServiceImpl(
            EventManagementRepository repository,
            EventIdempotencyStore idempotency,
            EventCacheInvalidator invalidation) {
        this(repository, idempotency, invalidation, (event, asset) -> {});
    }

    public EventManagementServiceImpl(
            EventManagementRepository repository,
            EventIdempotencyStore idempotency,
            EventCacheInvalidator invalidation,
            EventCoverService.Orphans orphans) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.invalidation = invalidation;
        this.orphans = Objects.requireNonNull(orphans);
    }

    @Override
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

    @Override
    public Result mutate(Actor actor, String key, EventCommand command) {
        EventManagementService.validateKey(key);
        String hash = command.hash();
        Optional<EventManagementRepository.Result> old = repository.receipt(actor, command.scope(), key, hash);
        if (old.isPresent()) {
            committed(old.get());
            return old.get();
        }
        repository.authorize(actor, command);
        EventIdempotencyStore.Lease lease = idempotency.acquire(EventManagementService.redisKey(actor, command.scope(), key), hash);
        try {
            EventManagementRepository.Result result = repository.command(actor, key, hash, command);
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
        if (result.cleanupJson() == null || result.eventId() == null) {
            return;
        }
        try {
            String id = EventResponseValues.string(result.cleanupJson(), "publicId").orElse(null);
            String url =
                    EventResponseValues.string(result.cleanupJson(), "coverImageUrl").orElse(null);
            if (id != null) {
                orphans.record(result.eventId(), new StoredAsset(url, id));
            }
        } catch (RuntimeException ignored) {
            LOGGER.warning(
                    "Event deletion cleanup journal pending; durable SQL metadata retained for reconciliation.");
        }
    }

    @Override
    public void invalidate(Result result) {
        try {
            invalidation.invalidateCommitted(result.eventId(), result.organizationId());
        } catch (RuntimeException ignored) {
            /* A committed result is never reported as a failed write. */
        }
    }

    

    
}
