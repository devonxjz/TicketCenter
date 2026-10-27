package vn.ticketscenter.event.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import vn.ticketscenter.event.*;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.dao.EventRepository;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.*;

import java.io.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;

import javax.sql.DataSource;

class EventServiceTest {
    private static final UUID EVENT = UUID.randomUUID(),
            ORG = UUID.randomUUID(),
            CATEGORY = UUID.randomUUID();
    private static final UserPrincipal ACTOR =
            new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 4);
    private static final EventFields FIELDS =
            new EventFields(
                    CATEGORY,
                    "My Event",
                    "Description",
                    "Venue",
                    "Address",
                    Instant.parse("2027-01-01T00:00:00Z"),
                    Instant.parse("2027-01-02T00:00:00Z"),
                    Instant.parse("2027-01-03T00:00:00Z"),
                    Instant.parse("2027-01-04T00:00:00Z"));

    private static EventDto result(String url, String publicId) {
        return new EventDto(
                EVENT,
                ORG,
                CATEGORY,
                FIELDS.title(),
                FIELDS.description(),
                FIELDS.venueName(),
                FIELDS.venueAddress(),
                FIELDS.saleStart(),
                FIELDS.saleEnd(),
                FIELDS.startTime(),
                FIELDS.endTime(),
                "DRAFT",
                1,
                url,
                publicId);
    }

    private static final class Repository extends EventRepository {
        EventDto saved = result("https://res.cloudinary.com/test/old.png", "legacy-old"), replay;
        boolean reject, rejectFinalize, ambiguous, aborted;
        int finalizes, releases;
        EventSave received;

        Repository() {
            super(
                    (DataSource)
                            Proxy.newProxyInstance(
                                    DataSource.class.getClassLoader(),
                                    new Class[] {DataSource.class},
                                    (p, m, a) -> {
                                        throw new AssertionError("Unexpected DB access");
                                    }));
        }

        @Override
        public Claim claim(
                UserPrincipal actor,
                EventSave command,
                String fingerprint,
                boolean hasImage,
                boolean maintenance) {
            if (reject) throw new EventException(403, "EVENT_FORBIDDEN", "Forbidden");
            return new Claim(
                    actor == null ? new UUID(0, 0) : actor.actorId(),
                    command.requestId(),
                    EVENT,
                    ORG,
                    UUID.randomUUID(),
                    hasImage ? "ticketcenter/events/" + EVENT + "/" + command.requestId() : null,
                    replay);
        }

        @Override
        public EventDto finalizeSave(
                UserPrincipal actor,
                EventSave command,
                Claim claim,
                StoredAsset asset,
                boolean maintenance) {
            if (rejectFinalize)
                throw new EventException(403, "EVENT_FORBIDDEN", "Membership was revoked");
            received = command;
            finalizes++;
            saved = asset == null ? saved : result(asset.secureUrl(), asset.publicId());
            if (ambiguous) {
                replay = saved;
                throw new EventException(503, "EVENT_DATABASE_UNAVAILABLE", "Unknown commit");
            }
            return saved;
        }

        @Override
        public void release(Claim claim, boolean abort) {
            releases++;
            aborted = abort;
        }
    }

    private static final class Storage implements StorageService {
        int uploads, deletes;
        boolean fail;

        @Override
        public StoredAsset ensure(InputStream in, String id) throws IOException {
            uploads++;
            if (fail) throw new IOException("Provider failure");
            return new StoredAsset("https://res.cloudinary.com/test/" + id + ".png", id);
        }

        @Override
        public StoredAsset upload(InputStream in, String folder) {
            throw new AssertionError("Random upload API used");
        }

        @Override
        public Optional<StoredAsset> find(String id) {
            return Optional.empty();
        }

        @Override
        public void delete(String id) {
            deletes++;
        }

        @Override
        public String getUrl(String id) {
            return null;
        }
    }

    private static byte[] png() {
        try {
            var output = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(
                    new java.awt.image.BufferedImage(16, 9, java.awt.image.BufferedImage.TYPE_INT_RGB),
                    "png",
                    output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void multipartEditKeepsCoverWhenNoFileSelected() {
        Repository r = new Repository();
        Storage s = new Storage();
        EventDto old = r.saved;
        assertEquals(
                old,
                new EventServiceImpl(r, s)
                        .save(
                                ACTOR,
                                new EventSave(
                                        UUID.randomUUID(),
                                        EventSave.Kind.EDIT,
                                        EVENT,
                                        0,
                                        FIELDS,
                                        null)));
        assertEquals(0, s.uploads);
        assertEquals(0, s.deletes);
    }

    @Test
    void createWithoutFileLeavesCoverForLaterUpload() {
        Repository r = new Repository();
        Storage s = new Storage();
        new EventServiceImpl(r, s)
                .save(
                        ACTOR,
                        new EventSave(
                                UUID.randomUUID(), EventSave.Kind.CREATE, ORG, 0, FIELDS, null));
        assertEquals(0, s.uploads);
        assertEquals(1, r.finalizes);
        assertNull(r.received.image());
    }

    @Test
    void permissionFailurePreventsUpload() {
        Repository r = new Repository();
        r.reject = true;
        Storage s = new Storage();
        assertEquals(
                403,
                assertThrows(
                                EventException.class,
                                () ->
                                        new EventServiceImpl(r, s)
                                                .save(
                                                        ACTOR,
                                                        new EventSave(
                                                                UUID.randomUUID(),
                                                                EventSave.Kind.COVER,
                                                                EVENT,
                                                                0,
                                                                null,
                                                                png())))
                        .status());
        assertEquals(0, s.uploads);
        assertEquals(0, r.finalizes);
    }

    @Test
    void revokedMembershipBlocksCommittedReplay() {
        Repository r = new Repository();
        r.replay = r.saved;
        r.reject = true;
        Storage s = new Storage();
        assertEquals(
                403,
                assertThrows(
                                EventException.class,
                                () ->
                                        new EventServiceImpl(r, s)
                                                .save(
                                                        ACTOR,
                                                        new EventSave(
                                                                UUID.randomUUID(),
                                                                EventSave.Kind.COVER,
                                                                EVENT,
                                                                0,
                                                                null,
                                                                png())))
                        .status());
        assertEquals(0, s.uploads);
    }

    @Test
    void revokedMembershipAtFinalizePreservesCoverAndAbortsIntent() {
        Repository r = new Repository();
        r.rejectFinalize = true;
        Storage s = new Storage();
        EventDto old = r.saved;
        assertEquals(
                403,
                assertThrows(
                                EventException.class,
                                () ->
                                        new EventServiceImpl(r, s)
                                                .save(
                                                        ACTOR,
                                                        new EventSave(
                                                                UUID.randomUUID(),
                                                                EventSave.Kind.COVER,
                                                                EVENT,
                                                                0,
                                                                null,
                                                                png())))
                        .status());
        assertEquals(old, r.saved);
        assertTrue(r.aborted);
        assertEquals(0, s.deletes);
    }

    @Test
    void uploadFailurePreservesOldCoverAndReleasesRetryLease() {
        Repository r = new Repository();
        Storage s = new Storage();
        s.fail = true;
        EventDto old = r.saved;
        assertEquals(
                502,
                assertThrows(
                                EventException.class,
                                () ->
                                        new EventServiceImpl(r, s)
                                                .save(
                                                        ACTOR,
                                                        new EventSave(
                                                                UUID.randomUUID(),
                                                                EventSave.Kind.COVER,
                                                                EVENT,
                                                                0,
                                                                null,
                                                                png())))
                        .status());
        assertEquals(old, r.saved);
        assertEquals(0, r.finalizes);
        assertEquals(1, r.releases);
        assertFalse(r.aborted);
    }

    @Test
    void unknownCommitReplaysConfirmedResultWithoutAnotherUpload() {
        Repository r = new Repository();
        r.ambiguous = true;
        Storage s = new Storage();
        EventService service = new EventServiceImpl(r, s);
        EventSave command =
                new EventSave(UUID.randomUUID(), EventSave.Kind.COVER, EVENT, 0, null, png());
        assertThrows(EventException.class, () -> service.save(ACTOR, command));
        EventDto committed = r.saved;
        assertEquals(committed, service.save(ACTOR, command));
        assertEquals(1, s.uploads);
        assertEquals(1, r.finalizes);
        assertEquals(0, s.deletes);
    }

    @Test
    void maintenanceCannotBeInvokedThroughSave() {
        Repository r = new Repository();
        Storage s = new Storage();
        EventService service = EventService.maintenance(r, s);
        assertEquals(
                403,
                assertThrows(
                                EventException.class,
                                () ->
                                        service.save(
                                                ACTOR,
                                                new EventSave(
                                                        UUID.randomUUID(),
                                                        EventSave.Kind.MIGRATE,
                                                        EVENT,
                                                        0,
                                                        null,
                                                        png())))
                        .status());
        assertEquals(0, s.uploads);
    }

    @Test
    void productionServiceCannotMigrate() {
        assertEquals(
                403,
                assertThrows(
                                EventException.class,
                                () ->
                                        new EventServiceImpl(new Repository(), new Storage())
                                                .migrateCover(EVENT, 0, UUID.randomUUID(), png()))
                        .status());
    }

    @Test
    void maintenanceCommandOnlyChangesCover() {
        Repository r = new Repository();
        EventService.maintenance(r, new Storage()).migrateCover(EVENT, 0, UUID.randomUUID(), png());
        assertEquals(EventSave.Kind.MIGRATE, r.received.kind());
        assertNull(r.received.fields());
    }

    @Test
    void fingerprintIncludesFieldsAndImageButDoesNotIncludeRetryKey() {
        EventSave a =
                new EventSave(UUID.randomUUID(), EventSave.Kind.EDIT, EVENT, 0, FIELDS, png());
        EventSave b =
                new EventSave(UUID.randomUUID(), EventSave.Kind.EDIT, EVENT, 0, FIELDS, png());
        assertEquals(
                EventServiceImpl.fingerprint(a, a.image()), EventServiceImpl.fingerprint(b, b.image()));
        assertNotEquals(
                EventServiceImpl.fingerprint(a, a.image()),
                EventServiceImpl.fingerprint(
                        new EventSave(b.requestId(), b.kind(), EVENT, 1, FIELDS, png()),
                        b.image()));
    }

    @Test void redisOutageLeavesDurableIntentRetryableWithoutFinalizingEvent() {
        var repository=new Repository();var storage=new Storage();
        var leases=new vn.ticketscenter.event.cache.EventIdempotencyStore(){
            public Lease acquire(String key,String hash){throw EventProblem.unavailable();}
            public void completed(Lease lease,String hash,vn.ticketscenter.event.dao.EventManagementRepository.Result result){fail();}
            public void release(Lease lease){fail();}
        };
        assertEquals(503,assertThrows(EventProblem.class,()->new EventServiceImpl(repository,storage,leases).save(ACTOR,new EventSave(UUID.randomUUID(),EventSave.Kind.EDIT,EVENT,0,FIELDS,null))).status());
        assertEquals(0,repository.finalizes);assertEquals(0,storage.uploads);assertEquals(1,repository.releases);assertFalse(repository.aborted);
    }
    @Test void redisCompletionFailurePreservesCommittedImageWorkflowResult() {
        var repository=new Repository();var storage=new Storage();var calls=new ArrayList<String>();
        var leases=new vn.ticketscenter.event.cache.EventIdempotencyStore(){
            public Lease acquire(String key,String hash){calls.add("acquire");return new Lease(key,"lease");}
            public void completed(Lease lease,String hash,vn.ticketscenter.event.dao.EventManagementRepository.Result result){assertEquals(1,repository.finalizes);calls.add("completed");throw new IllegalStateException("Redis lost after commit");}
            public void release(Lease lease){calls.add("release");}
        };
        assertEquals(repository.saved,new EventServiceImpl(repository,storage,leases).save(ACTOR,new EventSave(UUID.randomUUID(),EventSave.Kind.EDIT,EVENT,0,FIELDS,null)));
        assertEquals(List.of("acquire","completed","release"),calls);assertEquals(1,repository.finalizes);
    }
    @Test
    void durableSnapshotRoundTripsUnicodeQuotesAndNewlines() {
        EventDto dto =
                new EventDto(
                        EVENT,
                        ORG,
                        CATEGORY,
                        "Sự kiện \"đặc biệt\"",
                        "Line 1\nLine 2",
                        "Venue",
                        "Address",
                        FIELDS.saleStart(),
                        FIELDS.saleEnd(),
                        FIELDS.startTime(),
                        FIELDS.endTime(),
                        "DRAFT",
                        9007199254740991L,
                        null,
                        null);
        assertEquals(dto, EventDto.fromSnapshot(dto.snapshot()));
        assertTrue(dto.toJson().contains("\"version\":9007199254740991"));
        assertTrue(dto.toJson().contains("\"coverImageUrl\":null"));
    }
}
