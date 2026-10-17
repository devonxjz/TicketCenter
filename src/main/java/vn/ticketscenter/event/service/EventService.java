package vn.ticketscenter.event.service;

import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.image.EventImagePlaceholders;
import vn.ticketscenter.event.dao.EventRepository;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

public class EventService {
    private final EventRepository repository;
    private final StorageService storage;
    private final boolean maintenance;
    private final vn.ticketscenter.event.cache.EventIdempotencyStore redisLeases;

    public EventService(EventRepository repository, StorageService storage) {
        this(repository, storage, false);
    }

    public EventService(
            EventRepository repository,
            StorageService storage,
            vn.ticketscenter.event.cache.EventIdempotencyStore redisLeases) {
        this.repository = Objects.requireNonNull(repository);
        this.storage = Objects.requireNonNull(storage);
        this.maintenance = false;
        this.redisLeases = redisLeases;
    }

    private EventService(EventRepository repository, StorageService storage, boolean maintenance) {
        this.repository = Objects.requireNonNull(repository);
        this.storage = Objects.requireNonNull(storage);
        this.maintenance = maintenance;
        this.redisLeases = null;
    }

    public static EventService maintenance(EventRepository repository, StorageService storage) {
        return new EventService(repository, storage, true);
    }

    public EventDto save(UserPrincipal actor, EventSave command) {
        if (command.kind() == EventSave.Kind.MIGRATE)
            throw new EventException(
                    403,
                    "MAINTENANCE_FORBIDDEN",
                    "Migration is not available through Manager requests");
        return saveInternal(actor, command, false);
    }

    public EventDto migrateCover(UUID eventId, long version, UUID requestId, byte[] image) {
        if (!maintenance)
            throw new EventException(
                    403,
                    "MAINTENANCE_FORBIDDEN",
                    "Migration requires the CLI maintenance capability");
        return saveInternal(
                null,
                new EventSave(requestId, EventSave.Kind.MIGRATE, eventId, version, null, image),
                true);
    }

    private EventDto saveInternal(UserPrincipal actor, EventSave command, boolean maintenanceCall) {
        byte[] image = command.image();
        boolean defaultImage = image == null && command.kind() == EventSave.Kind.CREATE;
        try {
            if (image != null)
                image = ImageContentValidator.readAndValidate(new ByteArrayInputStream(image));
        } catch (IOException e) {
            throw new EventException(400, "IMAGE_INVALID", "Image cannot be decoded");
        }
        String fingerprint = fingerprint(command, image);
        EventRepository.Claim claim =
                repository.claim(
                        actor,
                        command,
                        fingerprint,
                        image != null || defaultImage,
                        maintenanceCall);
        if (claim.replay() != null) return claim.replay();
        vn.ticketscenter.event.cache.EventIdempotencyStore.Lease redisLease = null;
        if (redisLeases != null) {
            try {
                redisLease =
                        redisLeases.acquire(
                                "tc:idem:event-image:"
                                        + actor.actorId()
                                        + ":"
                                        + command.requestId(),
                                fingerprint);
            } catch (RuntimeException e) {
                release(claim, false);
                throw e;
            }
        }
        try {
            StoredAsset asset = null;
            if (image != null || defaultImage) {
                try {
                    if (defaultImage) {
                        asset = storage.find(claim.publicId()).orElse(null);
                        if (asset == null)
                            image =
                                    EventImagePlaceholders.png(
                                            repository.categorySlug(command.fields().categoryId()));
                    }
                    if (asset == null)
                        asset = storage.ensure(new ByteArrayInputStream(image), claim.publicId());
                } catch (EventException e) {
                    release(claim, e.status() < 500);
                    throw e;
                } catch (IOException | RuntimeException e) {
                    release(claim, false);
                    throw new EventException(
                            502,
                            "IMAGE_UPLOAD_FAILED",
                            "Image upload did not complete; retry with the same request key");
                }
            }
            try {
                EventDto saved =
                        repository.finalizeSave(actor, command, claim, asset, maintenanceCall);
                if (redisLease != null) {
                    try {
                        redisLeases.completed(
                                redisLease,
                                fingerprint,
                                new vn.ticketscenter.event.dao.EventManagementRepository.Result(
                                        200,
                                        "{\"data\":" + saved.toJson() + "}",
                                        saved.organizationId(),
                                        saved.id()));
                    } catch (RuntimeException ignored) {
                    }
                }
                return saved;
            } catch (EventException e) {
                if (e.status() < 500 && !e.code().equals("STALE_REQUEST")) release(claim, true);
                else release(claim, false);
                throw e;
            }
        } finally {
            if (redisLease != null) {
                try {
                    redisLeases.release(redisLease);
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    private void release(EventRepository.Claim claim, boolean abort) {
        try {
            repository.release(claim, abort);
        } catch (RuntimeException ignored) {
        }
    }

    static String fingerprint(EventSave command, byte[] image) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            field(digest, command.kind().name());
            field(digest, command.targetId().toString());
            field(digest, Long.toString(command.expectedVersion()));
            if (command.fields() != null) {
                EventFields f = command.fields();
                field(digest, f.categoryId().toString());
                field(digest, f.title());
                field(digest, f.description());
                field(digest, f.venueName());
                field(digest, f.venueAddress());
                field(digest, f.saleStart().toString());
                field(digest, f.saleEnd().toString());
                field(digest, f.startTime().toString());
                field(digest, f.endTime().toString());
            }
            field(
                    digest,
                    image == null
                            ? command.kind() == EventSave.Kind.CREATE ? "CATEGORY_DEFAULT" : "KEEP"
                            : HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(image)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void field(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    public List<EventDto> publicEvents() {
        return repository.publicEvents();
    }

    public EventDto publicEvent(UUID id) {
        return repository.publicEvent(id);
    }

    public List<EventDto> managedEvents(UserPrincipal actor, UUID org) {
        return repository.managedEvents(actor, org);
    }

    public EventDto managedEvent(UserPrincipal actor, UUID id) {
        return repository.managedEvent(actor, id);
    }

    public String options(UserPrincipal actor) {
        return repository.options(actor);
    }

    public List<EventRepository.MigrationImage> migrationInventory() {
        if (!maintenance)
            throw new EventException(
                    403, "MAINTENANCE_FORBIDDEN", "Migration requires CLI maintenance capability");
        return repository.migrationInventory();
    }

    public int cleanup(int limit) {
        int completed = 0;
        for (EventRepository.Cleanup task : repository.claimCleanup(limit)) {
            boolean success = false;
            try {
                storage.delete(task.publicId());
                success = true;
                completed++;
            } catch (IOException | RuntimeException ignored) {
            }
            repository.cleanupFinished(task, success);
        }
        return completed;
    }
}
