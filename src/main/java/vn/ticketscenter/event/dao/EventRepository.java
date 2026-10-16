package vn.ticketscenter.event.dao;

import static vn.ticketscenter.event.dao.EventJdbc.*;

import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.StoredAsset;

import java.sql.*;
import java.util.*;

import javax.sql.DataSource;

public class EventRepository {
    public record Claim(
            UUID actorId,
            UUID requestId,
            UUID eventId,
            UUID organizationId,
            UUID leaseToken,
            String publicId,
            EventDto replay) {}

    public record Cleanup(String publicId, UUID leaseToken) {}

    public record MigrationImage(
            EventDto event,
            String categorySlug,
            String imageUrl,
            String publicId,
            String originalCoverUrl) {}

    private static final UUID MAINTENANCE = new UUID(0, 0);
    private static final String COLUMNS =
            "e.id,e.organization_id,e.category_id,e.title,e.description,e.venue_name,"
                    + "e.venue_address,e.sale_start,e.sale_end,e.start_time,e.end_time,e.status,e.version,"
                    + "e.image_url,e.event_image_public_id";
    private final DataSource manager;
    private final DataSource buyer;
    private final DataSource worker;

    public EventRepository(DataSource source) {
        this(source, source, source);
    }

    public EventRepository(DataSource manager, DataSource buyer, DataSource worker) {
        this.manager = Objects.requireNonNull(manager);
        this.buyer = Objects.requireNonNull(buyer);
        this.worker = Objects.requireNonNull(worker);
    }

    public Claim claim(
            UserPrincipal actor,
            EventSave command,
            String fingerprint,
            boolean hasImage,
            boolean maintenance) {
        return tx(
                manager,
                c -> {
                    UUID actorId = maintenance ? null : Objects.requireNonNull(actor).actorId();
                    EventFields f = command.fields();
                    Object[] row =
                            row(
                                    c,
                                    "EXEC dbo.usp_EventImageClaim ?,?,?,?,?,?,?,?,?,?,?",
                                    actorId,
                                    maintenance ? null : actor.authVersion(),
                                    maintenance,
                                    command.requestId(),
                                    fingerprint,
                                    command.kind().name(),
                                    command.targetId(),
                                    command.expectedVersion(),
                                    f != null,
                                    f == null ? null : f.categoryId(),
                                    hasImage);
                    return new Claim(
                            uuid(row[0]),
                            command.requestId(),
                            uuid(row[1]),
                            uuid(row[2]),
                            uuid(row[3]),
                            (String) row[4],
                            row[5] == null ? null : EventDto.fromSnapshot((String) row[5]));
                });
    }

    public EventDto finalizeSave(
            UserPrincipal actor,
            EventSave command,
            Claim claim,
            StoredAsset asset,
            boolean maintenance) {
        return tx(
                manager,
                c -> {
                    if (!maintenance && actor == null)
                        throw failure(401, "AUTH_REQUIRED", "Sign in first");
                    EventFields f = command.fields();
                    Object[] saved =
                            row(
                                    c,
                                    "EXEC dbo.usp_EventImageSave"
                                            + " ?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?",
                                    maintenance ? null : actor.actorId(),
                                    maintenance ? null : actor.authVersion(),
                                    maintenance,
                                    claim.requestId(),
                                    claim.leaseToken(),
                                    claim.eventId(),
                                    claim.organizationId(),
                                    command.kind().name(),
                                    command.expectedVersion(),
                                    f != null,
                                    f == null ? null : f.categoryId(),
                                    f == null ? null : f.title(),
                                    f == null ? null : f.description(),
                                    f == null ? null : f.venueName(),
                                    f == null ? null : f.venueAddress(),
                                    f == null ? null : f.saleStart(),
                                    f == null ? null : f.saleEnd(),
                                    f == null ? null : f.startTime(),
                                    f == null ? null : f.endTime(),
                                    asset == null ? null : asset.publicId(),
                                    asset == null ? null : asset.secureUrl());
                    if (Boolean.TRUE.equals(saved[0]))
                        return EventDto.fromSnapshot((String) saved[1]);
                    EventDto result = find(c, claim.eventId());
                    execute(
                            c,
                            "EXEC dbo.usp_EventImageCommit ?,?,?,?,?,?",
                            claim.actorId(),
                            claim.requestId(),
                            claim.leaseToken(),
                            result.snapshot(),
                            saved[2],
                            asset == null ? null : asset.publicId());
                    return result;
                });
    }

    public void release(Claim claim, boolean abort) {
        tx(
                manager,
                c -> {
                    execute(
                            c,
                            "EXEC dbo.usp_EventImageRelease ?,?,?,?",
                            claim.actorId(),
                            claim.requestId(),
                            claim.leaseToken(),
                            abort);
                    return null;
                });
    }

    public List<EventDto> publicEvents() {
        return tx(
                buyer,
                c -> {
                    String sql =
                            "SELECT TOP(100)"
                                    + " event_id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,'PUBLISHED',CAST(0"
                                    + " AS bigint),cover_image_url,CAST(NULL AS varchar(255)) FROM"
                                    + " dbo.vw_PublicEvents e WHERE "
                                    + RealEventVisibility.predicate("e")
                                    + " ORDER BY start_time,event_id";
                    return events(c, sql);
                });
    }

    public EventDto publicEvent(UUID id) {
        return tx(
                buyer,
                c -> {
                    List<EventDto> rows =
                            events(
                                    c,
                                    "SELECT"
                                            + " event_id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,'PUBLISHED',CAST(0"
                                            + " AS bigint),cover_image_url,CAST(NULL AS varchar(255))"
                                            + " FROM dbo.vw_PublicEvents e WHERE event_id=? AND "
                                            + RealEventVisibility.predicate("e"),
                                    id);
                    if (rows.isEmpty())
                        throw failure(404, "EVENT_NOT_FOUND", "Event was not found");
                    return rows.getFirst();
                });
    }

    public List<EventDto> managedEvents(UserPrincipal actor, UUID organizationId) {
        return tx(
                manager,
                c -> {
                    authorize(c, actor, organizationId, false);
                    return events(
                            c,
                            "SELECT TOP(100) "
                                    + COLUMNS
                                    + " FROM dbo.vw_ManagedEvents e WHERE organization_id=? ORDER BY"
                                    + " start_time DESC,id",
                            organizationId);
                });
    }

    public EventDto managedEvent(UserPrincipal actor, UUID id) {
        return tx(
                manager,
                c -> {
                    EventDto event = find(c, id);
                    authorize(c, actor, event.organizationId(), false);
                    return event;
                });
    }

    public String options(UserPrincipal actor) {
        return tx(
                manager,
                c -> {
                    currentUser(c, actor);
                    List<String> organizations = new ArrayList<>(), categories = new ArrayList<>();
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT o.id,o.name FROM dbo.vw_ManagerOrganizations o"
                                                    + " WHERE o.user_id=? ORDER BY o.name",
                                            actor.actorId());
                            ResultSet r = q.executeQuery()) {
                        while (r.next())
                            organizations.add(
                                    "{\"id\":\""
                                            + id(r, 1)
                                            + "\",\"name\":"
                                            + vn.ticketscenter.config.web.HttpResponses.jsonString(
                                                    r.getString(2))
                                            + "}");
                    }
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT id,name,slug FROM dbo.vw_EventCategories ORDER"
                                                    + " BY name");
                            ResultSet r = q.executeQuery()) {
                        while (r.next())
                            categories.add(
                                    "{\"id\":\""
                                            + id(r, 1)
                                            + "\",\"name\":"
                                            + vn.ticketscenter.config.web.HttpResponses.jsonString(
                                                    r.getString(2))
                                            + ",\"slug\":"
                                            + vn.ticketscenter.config.web.HttpResponses.jsonString(
                                                    r.getString(3))
                                            + "}");
                    }
                    return "{\"organizations\":["
                            + String.join(",", organizations)
                            + "],\"categories\":["
                            + String.join(",", categories)
                            + "]}";
                });
    }

    public String categorySlug(UUID categoryId) {
        return tx(
                manager,
                c -> {
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT slug FROM dbo.vw_EventCategories WHERE id=?",
                                            categoryId);
                            ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw failure(400, "CATEGORY_NOT_FOUND", "Unknown category");
                        return r.getString(1);
                    }
                });
    }

    public List<MigrationImage> migrationInventory() {
        return tx(
                manager,
                c -> {
                    requireMaintenance(c);
                    List<MigrationImage> list = new ArrayList<>();
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT "
                                                    + COLUMNS
                                                    + ",e.category_slug,e.event_image,e.event_image"
                                                    + " FROM dbo.vw_ManagedEvents e ORDER BY e.id");
                            ResultSet r = q.executeQuery()) {
                        while (r.next())
                            list.add(
                                    new MigrationImage(
                                            event(r),
                                            r.getString(16),
                                            r.getString(14),
                                            r.getString(15),
                                            r.getString(18)));
                    }
                    return List.copyOf(list);
                });
    }

    public List<Cleanup> claimCleanup(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid cleanup limit");
        return tx(
                worker,
                c -> {
                    List<Cleanup> list = new ArrayList<>();
                    for (Object[] row : rows(c, "EXEC dbo.usp_EventImageClaimCleanup ?", limit))
                        list.add(new Cleanup((String) row[0], uuid(row[1])));
                    return List.copyOf(list);
                });
    }

    public void cleanupFinished(Cleanup task, boolean success) {
        tx(
                worker,
                c -> {
                    execute(
                            c,
                            "EXEC dbo.usp_EventImageCleanupFinished ?,?,?",
                            task.publicId(),
                            task.leaseToken(),
                            success);
                    return null;
                });
    }

    /** Session, manager membership and organization approval are checked by the database. */
    private static void authorize(
            Connection c, UserPrincipal actor, UUID organizationId, boolean maintenance)
            throws SQLException {
        if (!maintenance && actor == null) throw failure(401, "AUTH_REQUIRED", "Sign in first");
        execute(
                c,
                "EXEC dbo.usp_EventImageAuthorize ?,?,?,?",
                maintenance ? null : actor.actorId(),
                maintenance ? null : actor.authVersion(),
                maintenance ? null : organizationId,
                maintenance);
    }

    private static void currentUser(Connection c, UserPrincipal actor) throws SQLException {
        if (actor == null) throw failure(401, "AUTH_REQUIRED", "Sign in first");
        execute(c, "EXEC dbo.usp_EventRequireSession ?,?", actor.actorId(), actor.authVersion());
    }

    private static void requireMaintenance(Connection c) throws SQLException {
        authorize(c, null, null, true);
    }

    private static EventDto find(Connection c, UUID id) throws SQLException {
        List<EventDto> rows =
                events(c, "SELECT " + COLUMNS + " FROM dbo.vw_ManagedEvents e WHERE e.id=?", id);
        if (rows.isEmpty()) throw failure(404, "EVENT_NOT_FOUND", "Event was not found");
        return rows.getFirst();
    }
}
