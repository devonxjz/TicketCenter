package vn.ticketscenter.event.integration;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

import vn.ticketscenter.event.EventException;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.storage.StoredAsset;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;

import javax.sql.DataSource;

class EventImageSqlIT {
    EventImageFixture f;

    @BeforeEach
    void start() throws Exception {
        f = new EventImageFixture();
    }

    @AfterEach
    void stop() throws Exception {
        if (f != null) f.close();
    }

    @Test
    void createsEditsAndKeepsPersistedImage() throws Exception {
        String options = f.service.options(f.actor);
        assertTrue(options.contains("\"id\":\"" + f.organization + "\""));
        assertTrue(options.contains("\"id\":\"" + f.category + "\""));
        EventDto created = f.create();
        assertEquals("DRAFT", created.status());
        assertEquals(0, created.version());
        assertEquals(
                1,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_events WHERE id=? AND"
                                + " event_image IS NOT NULL AND event_image_public_id=?",
                        created.id(),
                        created.imagePublicId()));
        EventDto kept = f.service.save(f.actor, f.edit(created, "Edited without image", null));
        assertEquals(created.imagePublicId(), kept.imagePublicId());
        assertEquals(created.coverImageUrl(), kept.coverImageUrl());
        assertEquals(1, kept.version());
        EventDto replaced =
                f.service.save(f.actor, f.edit(kept, "Replaced", EventImageFixture.png(0xabcdef)));
        assertNotEquals(created.imagePublicId(), replaced.imagePublicId());
        assertEquals(2, replaced.version());
        assertEquals(replaced, f.service.managedEvent(f.actor, created.id()));
    }

    @Test
    void exactReplayAndPayloadConflict() throws Exception {
        EventSave command =
                new EventSave(
                        UUID.randomUUID(),
                        EventSave.Kind.CREATE,
                        f.organization,
                        0,
                        f.fields("Replay"),
                        EventImageFixture.png(7));
        EventDto first = f.service.save(f.actor, command);
        assertEquals(first, f.service.save(f.actor, command));
        assertEquals(1, f.storage.assets.size());
        EventSave conflict =
                new EventSave(
                        command.requestId(),
                        command.kind(),
                        command.targetId(),
                        0,
                        f.fields("Changed"),
                        command.image());
        assertCode("IDEMPOTENCY_CONFLICT", () -> f.service.save(f.actor, conflict));
        assertEquals(
                1,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_events WHERE organization_id=?",
                        f.organization));
    }

    @Test
    void noFileCreateReplayIgnoresLaterCategoryArtworkChanges() throws Exception {
        f.sql(
                "UPDATE dbo.tc_event_categories SET slug=? WHERE id=?",
                "music-" + f.category,
                f.category);
        EventSave command =
                new EventSave(
                        UUID.randomUUID(),
                        EventSave.Kind.CREATE,
                        f.organization,
                        0,
                        f.fields("Default image replay"),
                        null);
        EventDto first = f.service.save(f.actor, command);
        f.sql(
                "UPDATE dbo.tc_event_categories SET slug=? WHERE id=?",
                "sport-" + f.category,
                f.category);
        f.storage.uploaded =
                () -> {
                    throw new AssertionError("Committed replay contacted upload provider");
                };
        assertEquals(first, f.service.save(f.actor, command));
        assertEquals(1, f.storage.assets.size());
        assertEquals(1, f.service.managedEvents(f.actor, f.organization).size());
    }

    @Test
    void pendingDefaultImageUsesAcceptedAssetAfterCategoryChanges() throws Exception {
        f.sql(
                "UPDATE dbo.tc_event_categories SET slug=? WHERE id=?",
                "music-" + f.category,
                f.category);
        EventSave command =
                new EventSave(
                        UUID.randomUUID(),
                        EventSave.Kind.CREATE,
                        f.organization,
                        0,
                        f.fields("Pending default image"),
                        null);
        f.storage.uploaded =
                () -> {
                    throw new IllegalStateException(
                            "Injected upload acceptance without acknowledgement");
                };
        assertCode("IMAGE_UPLOAD_FAILED", () -> f.service.save(f.actor, command));
        assertEquals(1, f.storage.assets.size());
        StoredAsset accepted = f.storage.assets.values().iterator().next();
        assertEquals(
                1,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_event_image_requests WHERE actor_id=? AND"
                            + " request_id=? AND state='PENDING'",
                        f.user,
                        command.requestId()));
        assertEquals(0, f.service.managedEvents(f.actor, f.organization).size());
        f.sql(
                "UPDATE dbo.tc_event_categories SET slug=? WHERE id=?",
                "sport-" + f.category,
                f.category);
        f.storage.uploaded =
                () -> {
                    throw new AssertionError("Pending retry uploaded different artwork");
                };
        EventDto recovered = f.service.save(f.actor, command);
        assertEquals(accepted.secureUrl(), recovered.coverImageUrl());
        assertEquals(accepted.publicId(), recovered.imagePublicId());
        assertEquals(1, f.storage.assets.size());
        assertEquals(recovered, f.service.save(f.actor, command));
    }

    @Test
    void providerFailurePreservesOldAndRetriesSameIntent() {
        EventDto old = f.create();
        EventSave replacement = f.edit(old, "New", EventImageFixture.png(8));
        f.storage.fail = true;
        assertCode("IMAGE_UPLOAD_FAILED", () -> f.service.save(f.actor, replacement));
        assertEquals(old, f.service.managedEvent(f.actor, old.id()));
        f.storage.fail = false;
        EventDto result = f.service.save(f.actor, replacement);
        assertEquals("New", result.title());
        assertEquals(1, result.version());
    }

    @Test
    void sqlFailurePreservesOldAndKeepsRecoverableIntent() throws Exception {
        EventDto old = f.create();
        EventSave replacement = f.edit(old, "FAIL-SQL", EventImageFixture.png(9));
        f.sql(
                "CREATE OR ALTER TRIGGER dbo.liem43_fixture_reject ON dbo.tc_events AFTER UPDATE AS"
                        + " BEGIN IF EXISTS(SELECT 1 FROM inserted WHERE title='FAIL-SQL') THROW"
                        + " 59943,'Injected isolated fixture SQL failure',1; END");
        try {
            assertCode("EVENT_DATABASE_UNAVAILABLE", () -> f.service.save(f.actor, replacement));
            assertEquals(old, f.service.managedEvent(f.actor, old.id()));
            assertEquals(
                    1,
                    f.count(
                            "SELECT COUNT(*) FROM dbo.tc_event_image_requests WHERE actor_id=? AND"
                                    + " request_id=? AND state='PENDING'",
                            f.user,
                            replacement.requestId()));
        } finally {
            f.sql("DROP TRIGGER dbo.liem43_fixture_reject");
        }
        assertEquals(1, f.service.save(f.actor, replacement).version());
    }

    @Test
    void lostCommitAcknowledgementReplaysDurableSnapshot() {
        EventDto old = f.create();
        EventSave replacement = f.edit(old, "Commit survived", EventImageFixture.png(10));
        f.storage.uploaded = () -> f.source.loseNextCommit.set(true);
        assertCode("EVENT_DATABASE_UNAVAILABLE", () -> f.service.save(f.actor, replacement));
        f.storage.uploaded = () -> {};
        EventDto durable = f.service.save(f.actor, replacement);
        assertEquals(1, durable.version());
        assertEquals("Commit survived", durable.title());
        EventDto later = f.service.save(f.actor, f.edit(durable, "Later edit", null));
        assertEquals(2, later.version());
        assertEquals(durable, f.service.save(f.actor, replacement));
        assertFalse(f.storage.deleted.contains(durable.imagePublicId()));
    }

    @Test
    void revokedMembershipAfterUploadCannotFinalize() throws Exception {
        EventDto old = f.create();
        EventSave replacement = f.edit(old, "Forbidden", EventImageFixture.png(11));
        f.storage.uploaded =
                () -> {
                    try {
                        f.sql(
                                "UPDATE dbo.tc_organization_memberships SET active=0 WHERE"
                                        + " user_id=?",
                                f.user);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        assertCode("EVENT_FORBIDDEN", () -> f.service.save(f.actor, replacement));
        assertEquals(
                1,
                f.count(
                        "SELECT COUNT(*) FROM dbo.tc_events WHERE id=? AND version=0 AND"
                                + " event_image_public_id=?",
                        old.id(),
                        old.imagePublicId()));
    }

    @Test
    void currentUserCrossOrgAndReplayAuthorization() throws Exception {
        assertCode(
                "EVENT_FORBIDDEN",
                () ->
                        f.service.save(
                                f.actor,
                                new EventSave(
                                        UUID.randomUUID(),
                                        EventSave.Kind.CREATE,
                                        f.foreign,
                                        0,
                                        f.fields("Foreign"),
                                        EventImageFixture.png(12))));
        EventSave command =
                new EventSave(
                        UUID.randomUUID(),
                        EventSave.Kind.CREATE,
                        f.organization,
                        0,
                        f.fields("Authorized"),
                        EventImageFixture.png(13));
        f.service.save(f.actor, command);
        f.sql("UPDATE dbo.tc_users SET auth_version=1 WHERE id=?", f.user);
        assertCode("SESSION_EXPIRED", () -> f.service.save(f.actor, command));
        f.sql("UPDATE dbo.tc_users SET auth_version=0,status='DISABLED' WHERE id=?", f.user);
        assertCode("SESSION_EXPIRED", () -> f.service.save(f.actor, command));
    }

    @Test
    void twoConnectionsRaceSameVersionOneWins() throws Exception {
        EventDto old = f.create();
        EventSave a = f.edit(old, "A", EventImageFixture.png(14)),
                b = f.edit(old, "B", EventImageFixture.png(15));
        CyclicBarrier barrier = new CyclicBarrier(2);
        f.storage.uploaded =
                () -> {
                    try {
                        barrier.await(20, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures =
                    List.of(pool.submit(() -> outcome(a)), pool.submit(() -> outcome(b)));
            List<Object> results = new ArrayList<>();
            for (Future<Object> task : futures) results.add(task.get(40, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(EventDto.class::isInstance).count());
            assertEquals(
                    1,
                    results.stream()
                            .filter(
                                    x ->
                                            x instanceof EventException e
                                                    && e.code().equals("EVENT_VERSION_CONFLICT"))
                            .count());
        }
        assertEquals(1, f.service.managedEvent(f.actor, old.id()).version());
    }

    @Test
    void cleanupPreservesSharedReferenceAndPendingAttach() throws Exception {
        EventDto first = f.create(), second = f.create();
        f.sql(
                "UPDATE dbo.tc_events SET event_image=?,event_image_public_id=?"
                        + " WHERE id=?",
                first.coverImageUrl(),
                first.imagePublicId(),
                second.id());
        f.service.save(f.actor, f.edit(first, "Replaced", EventImageFixture.png(16)));
        f.service.cleanup(100);
        assertFalse(f.storage.deleted.contains(first.imagePublicId()));
        f.sql(
                "UPDATE dbo.tc_events SET"
                        + " event_image=NULL,event_image_public_id=NULL WHERE"
                        + " id=?",
                second.id());
        f.service.cleanup(100);
        assertTrue(f.storage.deleted.contains(first.imagePublicId()));
        EventSave pending =
                f.edit(
                        f.service.managedEvent(f.actor, first.id()),
                        "Pending",
                        EventImageFixture.png(17));
        EventRepository repository = new EventRepository(f.source);
        EventRepository.Claim claim =
                repository.claim(f.actor, pending, "a".repeat(64), true, false);
        f.service.cleanup(100);
        assertFalse(f.storage.deleted.contains(claim.publicId()));
        repository.release(claim, true);
        f.service.cleanup(100);
        assertTrue(f.storage.deleted.contains(claim.publicId()));
    }

    @Test
    void runtimeManagerBuyerAndWorkerGrantsSupportTheRealWorkflow() throws Exception {
        Map<String, String> users = new LinkedHashMap<>();
        for (String role : List.of("tc_manager", "tc_buyer", "tc_worker")) {
            String user = role + "_" + UUID.randomUUID().toString().replace("-", "");
            f.sql(
                    "CREATE USER ["
                            + user
                            + "] WITHOUT LOGIN; ALTER ROLE ["
                            + role
                            + "] ADD MEMBER ["
                            + user
                            + "];");
            users.put(role, user);
        }
        try {
            EventService service =
                    new EventService(
                            new EventRepository(
                                    roleSource(users.get("tc_manager")),
                                    roleSource(users.get("tc_buyer")),
                                    roleSource(users.get("tc_worker"))),
                            f.storage);
            EventDto draft =
                    service.save(
                            f.actor,
                            new EventSave(
                                    UUID.randomUUID(),
                                    EventSave.Kind.CREATE,
                                    f.organization,
                                    0,
                                    f.fields("Runtime roles"),
                                    EventImageFixture.png(28)));
            assertEquals(draft, service.managedEvent(f.actor, draft.id()));
            f.sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", draft.id());
            assertEquals("Runtime roles", service.publicEvent(draft.id()).title());
            assertTrue(
                    service.publicEvents().stream()
                            .anyMatch(event -> event.id().equals(draft.id())));
            assertEquals(0, service.cleanup(100));
        } finally {
            for (String user : users.values()) f.sql("DROP USER [" + user + "]");
        }
    }

    private DataSource roleSource(String user) {
        return (DataSource)
                Proxy.newProxyInstance(
                        DataSource.class.getClassLoader(),
                        new Class<?>[] {DataSource.class},
                        (proxy, method, args) -> {
                            try {
                                Object result = method.invoke(f.source, args);
                                if (result instanceof Connection connection) {
                                    try (var query = connection.createStatement()) {
                                        query.execute("EXECUTE AS USER='" + user + "'");
                                    }
                                }
                                return result;
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }

    private Object outcome(EventSave command) {
        try {
            return f.service.save(f.actor, command);
        } catch (EventException e) {
            return e;
        }
    }

    static void assertCode(String expected, Runnable run) {
        assertEquals(expected, assertThrows(EventException.class, run::run).code());
    }
}
