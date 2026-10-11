package vn.ticketscenter.event.repository;

import static vn.ticketscenter.event.repository.EventJdbc.*;

import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.StoredAsset;

import java.sql.*;
import java.time.*;
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
                + "COALESCE(NULLIF(e.event_image,''),e.cover_image_url),e.event_image_public_id";
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
                    UUID actorId =
                            maintenance ? MAINTENANCE : Objects.requireNonNull(actor).actorId();
                    UUID eventId =
                            command.kind() == EventSave.Kind.CREATE
                                    ? UUID.randomUUID()
                                    : command.targetId();
                    UUID organizationId;
                    if (command.kind() == EventSave.Kind.CREATE)
                        organizationId = command.targetId();
                    else organizationId = find(c, command.targetId(), false).organizationId();
                    authorize(c, actor, organizationId, maintenance);
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT"
                                                + " fingerprint,event_id,organization_id,public_id,state,lease_until,result_json"
                                                + " FROM dbo.tc_event_image_requests"
                                                + " WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=? AND"
                                                + " request_id=?",
                                            actorId,
                                            command.requestId());
                            ResultSet r = q.executeQuery()) {
                        if (r.next()) {
                            if (!fingerprint.equals(r.getString(1)))
                                throw failure(
                                        409,
                                        "IDEMPOTENCY_CONFLICT",
                                        "Request key was already used for different input");
                            eventId = id(r, 2);
                            organizationId = id(r, 3);
                            authorize(c, actor, organizationId, maintenance);
                            if ("COMMITTED".equals(r.getString(5)))
                                return new Claim(
                                        actorId,
                                        command.requestId(),
                                        eventId,
                                        organizationId,
                                        null,
                                        r.getString(4),
                                        EventDto.fromSnapshot(r.getString(7)));
                            if ("ABORTED".equals(r.getString(5)))
                                throw failure(
                                        409,
                                        "REQUEST_ABORTED",
                                        "Save has been cancelled; use a new request key");
                            if (instant(r, 6).isAfter(Instant.now()))
                                throw failure(
                                        409,
                                        "REQUEST_IN_PROGRESS",
                                        "Save is still running; retry shortly");
                            checkTarget(c, command, eventId);
                            UUID token = UUID.randomUUID();
                            execute(
                                    c,
                                    "UPDATE dbo.tc_event_image_requests SET"
                                        + " lease_token=?,lease_until=DATEADD(minute,10,SYSUTCDATETIME()),updated_at=SYSUTCDATETIME()"
                                        + " WHERE actor_id=? AND request_id=?",
                                    token,
                                    actorId,
                                    command.requestId());
                            return new Claim(
                                    actorId,
                                    command.requestId(),
                                    eventId,
                                    organizationId,
                                    token,
                                    r.getString(4),
                                    null);
                        }
                    }
                    checkTarget(c, command, eventId);
                    UUID token = UUID.randomUUID();
                    String publicId =
                            hasImage
                                    ? "ticketcenter/events/" + eventId + "/" + command.requestId()
                                    : null;
                    execute(
                            c,
                            "INSERT"
                                + " dbo.tc_event_image_requests(actor_id,request_id,fingerprint,operation,event_id,organization_id,expected_version,public_id,state,lease_token,lease_until)"
                                + " VALUES(?,?,?,?,?,?,?,?,"
                                + " 'PENDING',?,DATEADD(minute,10,SYSUTCDATETIME()))",
                            actorId,
                            command.requestId(),
                            fingerprint,
                            command.kind().name(),
                            eventId,
                            organizationId,
                            command.expectedVersion(),
                            publicId,
                            token);
                    if (publicId != null)
                        execute(
                                c,
                                "INSERT dbo.tc_event_image_assets(public_id,event_id,state)"
                                        + " VALUES(?,?,'AVAILABLE')",
                                publicId,
                                eventId);
                    return new Claim(
                            actorId,
                            command.requestId(),
                            eventId,
                            organizationId,
                            token,
                            publicId,
                            null);
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
                    authorize(c, actor, claim.organizationId(), maintenance);
                    try (PreparedStatement q =
                                    query(
                                            c,
                                            "SELECT state,lease_token,result_json FROM"
                                                    + " dbo.tc_event_image_requests"
                                                    + " WITH(UPDLOCK,HOLDLOCK) WHERE actor_id=? AND"
                                                    + " request_id=?",
                                            claim.actorId(),
                                            claim.requestId());
                            ResultSet r = q.executeQuery()) {
                        if (!r.next()) throw failure(409, "REQUEST_LOST", "Save intent is missing");
                        if ("COMMITTED".equals(r.getString(1)))
                            return EventDto.fromSnapshot(r.getString(3));
                        if (!"PENDING".equals(r.getString(1))
                                || !claim.leaseToken().equals(id(r, 2)))
                            throw failure(409, "STALE_REQUEST", "Save lease has changed; retry");
                    }
                    checkTarget(c, command, claim.eventId());
                    if (asset != null) {
                        if (!claim.publicId().equals(asset.publicId())
                                || !asset.secureUrl().startsWith("https://res.cloudinary.com/")
                                || asset.secureUrl().length() > 2048)
                            throw failure(
                                    502,
                                    "IMAGE_METADATA_INVALID",
                                    "Image provider returned invalid metadata");
                        try (PreparedStatement q =
                                        query(
                                                c,
                                                "SELECT state FROM dbo.tc_event_image_assets"
                                                    + " WITH(UPDLOCK,HOLDLOCK) WHERE public_id=?",
                                                asset.publicId());
                                ResultSet r = q.executeQuery()) {
                            if (!r.next() || !"AVAILABLE".equals(r.getString(1)))
                                throw failure(
                                        409, "ASSET_UNAVAILABLE", "Image is scheduled for cleanup");
                        }
                        execute(
                                c,
                                "UPDATE dbo.tc_event_image_assets SET secure_url=? WHERE"
                                        + " public_id=?",
                                asset.secureUrl(),
                                asset.publicId());
                    }
                    String oldId =
                            command.kind() == EventSave.Kind.CREATE
                                    ? null
                                    : find(c, claim.eventId(), false).imagePublicId();
                    if (command.kind() == EventSave.Kind.CREATE) {
                        EventFields f = command.fields();
                        execute(
                                c,
                                "INSERT"
                                    + " dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,sale_start,sale_end,start_time,end_time,status,version,event_image,event_image_public_id,cover_image_url)"
                                    + " VALUES(?,?,?,?,?,?,?,?,?,?,?,'DRAFT',0,?,?,?)",
                                claim.eventId(),
                                claim.organizationId(),
                                f.categoryId(),
                                f.title(),
                                f.description(),
                                f.venueName(),
                                f.venueAddress(),
                                f.saleStart(),
                                f.saleEnd(),
                                f.startTime(),
                                f.endTime(),
                                asset == null ? null : asset.secureUrl(),
                                asset == null ? null : asset.publicId(),
                                asset == null ? null : asset.secureUrl());
                    } else {
                        String sql = "UPDATE dbo.tc_events SET version=version+1";
                        List<Object> args = new ArrayList<>();
                        if (command.kind() == EventSave.Kind.EDIT) {
                            EventFields f = command.fields();
                            sql +=
                                    ",category_id=?,title=?,description=?,venue_name=?,venue_address=?,sale_start=?,sale_end=?,start_time=?,end_time=?";
                            Collections.addAll(
                                    args,
                                    f.categoryId(),
                                    f.title(),
                                    f.description(),
                                    f.venueName(),
                                    f.venueAddress(),
                                    f.saleStart(),
                                    f.saleEnd(),
                                    f.startTime(),
                                    f.endTime());
                        }
                        if (asset != null) {
                            sql += ",event_image=?,event_image_public_id=?,cover_image_url=?";
                            Collections.addAll(
                                    args, asset.secureUrl(), asset.publicId(), asset.secureUrl());
                        }
                        sql += " WHERE id=? AND version=?";
                        Collections.addAll(args, claim.eventId(), command.expectedVersion());
                        if (execute(c, sql, args.toArray()) != 1)
                            throw failure(
                                    409,
                                    "EVENT_VERSION_CONFLICT",
                                    "Event has changed; reload before saving");
                    }
                    EventDto result = find(c, claim.eventId(), false);
                    execute(
                            c,
                            "UPDATE dbo.tc_event_image_requests SET"
                                    + " state='COMMITTED',result_json=?,updated_at=SYSUTCDATETIME()"
                                    + " WHERE actor_id=? AND request_id=?",
                            result.snapshot(),
                            claim.actorId(),
                            claim.requestId());
                    if (asset != null && oldId != null && !oldId.equals(asset.publicId()))
                        execute(
                                c,
                                "UPDATE dbo.tc_event_image_assets SET available_at=SYSUTCDATETIME()"
                                        + " WHERE public_id=? AND state='AVAILABLE'",
                                oldId);
                    return result;
                });
    }

    public void release(Claim claim, boolean abort) {
        tx(
                manager,
                c -> {
                    execute(
                            c,
                            "UPDATE dbo.tc_event_image_requests SET"
                                + " state=?,lease_until=SYSUTCDATETIME(),updated_at=SYSUTCDATETIME()"
                                + " WHERE actor_id=? AND request_id=? AND lease_token=? AND"
                                + " state='PENDING'",
                            abort ? "ABORTED" : "PENDING",
                            claim.actorId(),
                            claim.requestId(),
                            claim.leaseToken());
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
                                + " dbo.vw_PublicEvents ORDER BY start_time,event_id";
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
                                        + " FROM dbo.vw_PublicEvents WHERE event_id=?",
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
                                    + " FROM dbo.tc_events e WHERE organization_id=? ORDER BY"
                                    + " start_time DESC,id",
                            organizationId);
                });
    }

    public EventDto managedEvent(UserPrincipal actor, UUID id) {
        return tx(
                manager,
                c -> {
                    EventDto event = find(c, id, false);
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
                                            "SELECT o.id,o.name FROM dbo.tc_organizations o JOIN"
                                                + " dbo.tc_organization_memberships m ON"
                                                + " m.organization_id=o.id WHERE m.user_id=? AND"
                                                + " m.active=1 AND m.role='MANAGER' AND"
                                                + " o.status='APPROVED' ORDER BY o.name",
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
                                            "SELECT id,name,slug FROM dbo.tc_event_categories ORDER"
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
                                            "SELECT slug FROM dbo.tc_event_categories WHERE id=?",
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
                                                    + ",cat.slug,e.event_image,e.cover_image_url"
                                                    + " FROM dbo.tc_events e JOIN"
                                                    + " dbo.tc_event_categories cat ON"
                                                    + " cat.id=e.category_id ORDER BY e.id");
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
                    execute(
                            c,
                            "UPDATE dbo.tc_event_image_requests SET"
                                    + " state='ABORTED',updated_at=SYSUTCDATETIME() WHERE"
                                    + " state='PENDING' AND lease_until<SYSUTCDATETIME() AND"
                                    + " updated_at<DATEADD(day,-1,SYSUTCDATETIME())");
                    List<Cleanup> list = new ArrayList<>();
                    String sql =
                            "SELECT TOP(?) a.public_id FROM dbo.tc_event_image_assets a"
                                + " WITH(UPDLOCK,HOLDLOCK) WHERE a.state IN"
                                + " ('AVAILABLE','DELETE_PENDING') AND"
                                + " a.available_at<=SYSUTCDATETIME() AND (a.lease_until IS NULL OR"
                                + " a.lease_until<SYSUTCDATETIME()) AND NOT EXISTS(SELECT 1 FROM"
                                + " dbo.tc_events e WHERE e.event_image_public_id=a.public_id OR"
                                + " (a.secure_url IS NOT NULL AND (e.event_image=a.secure_url OR"
                                + " e.cover_image_url=a.secure_url))) AND NOT EXISTS(SELECT 1 FROM"
                                + " dbo.tc_users u WHERE u.avatar_public_id=a.public_id OR"
                                + " (a.secure_url IS NOT NULL AND u.avatar=a.secure_url)) AND NOT"
                                + " EXISTS(SELECT 1 FROM dbo.tc_event_image_requests r"
                                + " WITH(UPDLOCK,HOLDLOCK) WHERE r.public_id=a.public_id AND"
                                + " r.state='PENDING') ORDER BY a.available_at,a.public_id";
                    try (PreparedStatement q = query(c, sql, limit);
                            ResultSet r = q.executeQuery()) {
                        while (r.next()) {
                            String publicId = r.getString(1);
                            UUID token = UUID.randomUUID();
                            if (!publicId.matches(
                                    "ticketcenter/events/[0-9a-fA-F-]{36}/[0-9a-fA-F-]{36}"))
                                continue;
                            execute(
                                    c,
                                    "UPDATE dbo.tc_event_image_assets SET"
                                        + " state='DELETE_PENDING',lease_token=?,lease_until=DATEADD(minute,10,SYSUTCDATETIME()),attempts=attempts+1"
                                        + " WHERE public_id=?",
                                    token,
                                    publicId);
                            list.add(new Cleanup(publicId, token));
                        }
                    }
                    return List.copyOf(list);
                });
    }

    public void cleanupFinished(Cleanup task, boolean success) {
        tx(
                worker,
                c -> {
                    execute(
                            c,
                            "UPDATE dbo.tc_event_image_assets SET"
                                + " state=?,lease_until=NULL,lease_token=NULL,available_at=DATEADD(minute,?,SYSUTCDATETIME())"
                                + " WHERE public_id=? AND lease_token=? AND state='DELETE_PENDING'",
                            success ? "DELETED" : "DELETE_PENDING",
                            success ? 1440 : 5,
                            task.publicId(),
                            task.leaseToken());
                    return null;
                });
    }

    private static void checkTarget(Connection c, EventSave command, UUID eventId)
            throws SQLException {
        if (command.fields() != null) {
            try (PreparedStatement q =
                            query(
                                    c,
                                    "SELECT 1 FROM dbo.tc_event_categories WHERE id=?",
                                    command.fields().categoryId());
                    ResultSet r = q.executeQuery()) {
                if (!r.next()) throw failure(400, "CATEGORY_NOT_FOUND", "Unknown category");
            }
        }
        if (command.kind() == EventSave.Kind.CREATE) return;
        EventDto event = find(c, eventId, true);
        if (event.version() != command.expectedVersion())
            throw failure(409, "EVENT_VERSION_CONFLICT", "Event has changed; reload before saving");
        if (command.kind() == EventSave.Kind.EDIT
                && !Set.of("DRAFT", "REJECTED").contains(event.status()))
            throw failure(409, "EVENT_NOT_EDITABLE", "Only draft or rejected Events can be edited");
        if (command.kind() == EventSave.Kind.COVER
                && !Set.of("DRAFT", "REJECTED", "PUBLISHED").contains(event.status()))
            throw failure(409, "EVENT_NOT_EDITABLE", "Event cover cannot be changed in this state");
    }

    private static void authorize(
            Connection c, UserPrincipal actor, UUID organizationId, boolean maintenance)
            throws SQLException {
        if (maintenance) {
            requireMaintenance(c);
            return;
        }
        currentUser(c, actor);
        try (PreparedStatement q =
                        query(
                                c,
                                "SELECT 1 FROM dbo.tc_organization_memberships"
                                        + " WITH(UPDLOCK,HOLDLOCK) WHERE user_id=? AND"
                                        + " organization_id=? AND active=1 AND role='MANAGER'",
                                actor.actorId(),
                                organizationId);
                ResultSet r = q.executeQuery()) {
            if (!r.next())
                throw failure(403, "EVENT_FORBIDDEN", "Current Manager membership is required");
        }
        try (PreparedStatement q =
                        query(
                                c,
                                "SELECT 1 FROM dbo.tc_organizations WITH(HOLDLOCK) WHERE id=? AND"
                                        + " status='APPROVED'",
                                organizationId);
                ResultSet r = q.executeQuery()) {
            if (!r.next())
                throw failure(403, "ORGANIZATION_NOT_APPROVED", "Organization is not approved");
        }
    }

    private static void currentUser(Connection c, UserPrincipal actor) throws SQLException {
        if (actor == null) throw failure(401, "AUTH_REQUIRED", "Sign in first");
        try (PreparedStatement q =
                        query(
                                c,
                                "SELECT 1 FROM dbo.tc_users WITH(UPDLOCK,HOLDLOCK) WHERE id=? AND"
                                        + " status='ACTIVE' AND auth_version=?",
                                actor.actorId(),
                                actor.authVersion());
                ResultSet r = q.executeQuery()) {
            if (!r.next())
                throw failure(401, "SESSION_EXPIRED", "Current session is no longer valid");
        }
    }

    private static void requireMaintenance(Connection c) throws SQLException {
        try (PreparedStatement q =
                        query(
                                c,
                                "SELECT CASE WHEN USER_NAME()='dbo' OR IS_ROLEMEMBER('db_owner')=1"
                                        + " OR IS_SRVROLEMEMBER('sysadmin')=1 THEN 1 ELSE 0 END");
                ResultSet r = q.executeQuery()) {
            if (!r.next() || !r.getBoolean(1))
                throw failure(
                        403,
                        "MAINTENANCE_FORBIDDEN",
                        "Database owner connection is required for maintenance");
        }
    }

    private static EventDto find(Connection c, UUID id, boolean lock) throws SQLException {
        List<EventDto> rows =
                events(
                        c,
                        "SELECT "
                                + COLUMNS
                                + " FROM dbo.tc_events e "
                                + (lock ? "WITH(UPDLOCK,HOLDLOCK) " : "")
                                + "WHERE e.id=?",
                        id);
        if (rows.isEmpty()) throw failure(404, "EVENT_NOT_FOUND", "Event was not found");
        return rows.getFirst();
    }
}
