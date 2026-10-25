package vn.ticketscenter.event.service;

import vn.ticketscenter.event.cache.*;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.dao.EventManagementRepository;
import vn.ticketscenter.event.dao.EventManagementRepository.*;
import vn.ticketscenter.storage.*;
import java.io.*;
import java.net.URI;
import java.util.*;

public final class EventCoverServiceImpl implements EventCoverService {
    

    private final EventManagementRepository repository;
    private final EventIdempotencyStore idempotency;
    private final EventManagementService mutations;
    private final StorageService storage;
    private final Orphans orphans;

    public EventCoverServiceImpl(
            EventManagementRepository repository,
            EventIdempotencyStore idempotency,
            EventManagementService mutations,
            StorageService storage,
            Orphans orphans) {
        this.repository = repository;
        this.idempotency = idempotency;
        this.mutations = mutations;
        this.storage = storage;
        this.orphans = orphans;
    }

    @Override
    public Result upload(Actor actor, UUID event, String key, InputStream content)
            throws IOException {
        EventManagementService.validateKey(key);
        var scope = new EventCommand("COVER", null, event, null, Map.of());
        repository.authorize(actor, scope); // Before reading/uploading an untrusted stream.
        byte[] bytes;
        try {
            bytes = ImageContentValidator.readAndValidate(content);
        } catch (IOException e) {
            throw EventProblem.invalid("Không thể giải mã tệp ảnh.");
        }
        String hash = EventCommand.sha256("COVER|" + event + "|" + EventCommand.sha256(bytes));
        var prior = repository.receipt(actor, "EVENT", key, hash);
        if (prior.isPresent()) {
            mutations.invalidate(prior.get());
            return prior.get();
        }
        var context = repository.coverContext(actor, event);
        if (!Set.of("DRAFT", "REJECTED").contains(context.status()))
            throw EventProblem.conflict("Sự kiện đang khóa thông tin ảnh.");
        String expectedVersion = Long.toString(context.version());
        var lease = idempotency.acquire(EventManagementService.redisKey(actor, "EVENT", key), hash);
        StoredAsset asset = null;
        try {
            try {
                asset =
                        storage.upload(
                                new ByteArrayInputStream(bytes), "ticketscenter/events/" + event);
            } catch (IOException | RuntimeException e) {
                throw new EventProblem(
                        502,
                        "EVENT_STORAGE_UNAVAILABLE",
                        "Dịch vụ ảnh chưa thể nhận tệp. Vui lòng thử lại.");
            }
            if (asset == null || asset.secureUrl() == null || asset.publicId() == null)
                throw new EventProblem(
                        502, "EVENT_STORAGE_INVALID", "Dịch vụ ảnh trả metadata không hợp lệ.");
            URI url;
            try {
                url = URI.create(asset.secureUrl());
            } catch (IllegalArgumentException e) {
                throw new EventProblem(
                        502, "EVENT_STORAGE_INVALID", "Dịch vụ ảnh trả metadata không hợp lệ.");
            }
            if ((!StorageServiceImpl.isLocalUrl(asset.secureUrl())
                            && (!"https".equalsIgnoreCase(url.getScheme())
                                    || url.getHost() == null
                                    || url.getUserInfo() != null))
                    || !asset.publicId().matches("[A-Za-z0-9/_-]{1,255}"))
                throw new EventProblem(
                        502, "EVENT_STORAGE_INVALID", "Dịch vụ ảnh trả metadata không hợp lệ.");
            var command =
                    new EventCommand(
                            "COVER",
                            null,
                            event,
                            null,
                            Map.of(
                                    "coverImageUrl",
                                    asset.secureUrl(),
                                    "publicId",
                                    asset.publicId(),
                                    "expectedVersion",
                                    expectedVersion));
            var result =
                    repository.command(
                            actor, key, hash, command); // New short SQL transaction after upload.
            boolean used =
                    EventResponseValues.string(result.json(), "coverImageUrl")
                            .filter(asset.secureUrl()::equals)
                            .isPresent();
            if (!used) record(event, asset);
            else if (context.publicId() != null && !context.publicId().equals(asset.publicId()))
                record(event, new StoredAsset(context.coverUrl(), context.publicId()));
            mutations.invalidate(result);
            try {
                idempotency.completed(lease, hash, result);
            } catch (RuntimeException ignored) {
            }
            return result;
        } catch (RuntimeException e) {
            if (asset != null) {
                // A lost commit acknowledgement is ambiguous; record for DB-verified cleanup, never
                // delete blindly.
                record(event, asset);
            }
            try {
                idempotency.release(lease);
            } catch (RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void record(UUID event, StoredAsset asset) {
        try {
            orphans.record(event, asset);
        } catch (RuntimeException ignored) {
        }
    }
}
