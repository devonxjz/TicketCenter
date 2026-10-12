package vn.ticketscenter.event.integration;

import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.repository.EventRepository;
import vn.ticketscenter.event.service.EventService;
import vn.ticketscenter.identity.model.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.storage.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.sql.DataSource;

final class EventImageFixture implements AutoCloseable {
    static final String DATABASE = "TicketCenter_LIEM43_Test";
    final UUID user = UUID.randomUUID(),
            keeper = UUID.randomUUID(),
            organization = UUID.randomUUID(),
            foreign = UUID.randomUUID(),
            category = UUID.randomUUID();
    final UserPrincipal actor =
            new UserPrincipal(user, PlatformRole.CUSTOMER, 0, OrganizationRole.MANAGER);
    final Source source = new Source();
    final FakeStorage storage = new FakeStorage();
    final EventService service;

    EventImageFixture() throws SQLException {
        try (Connection c = source.getConnection();
                Statement q = c.createStatement();
                ResultSet r = q.executeQuery("SELECT DB_NAME()")) {
            r.next();
            if (!DATABASE.equals(r.getString(1)))
                throw new IllegalStateException("Refusing non-isolated SQL target");
        }
        sql(
                "INSERT dbo.tc_users(id,email,password_hash,full_name,user_name,email_verified_at)"
                    + " VALUES(?,?,'fixture-only','Fixture',?,SYSUTCDATETIME())",
                user,
                user + "@example.test",
                "fixture-" + user);
        sql(
                "INSERT dbo.tc_users(id,email,password_hash,full_name,user_name,email_verified_at)"
                    + " VALUES(?,?,'fixture-only','Fixture keeper',?,SYSUTCDATETIME())",
                keeper,
                keeper + "@example.test",
                "fixture-" + keeper);
        sql(
                "INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,'Fixture',?)",
                category,
                "fixture-" + category);
        sql(
                "INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Fixture',?)",
                organization,
                organization + "@example.test");
        sql(
                "INSERT dbo.tc_organizations(id,name,contact_email) VALUES(?,'Foreign',?)",
                foreign,
                foreign + "@example.test");
        sql(
                "INSERT dbo.tc_organization_memberships(user_id,organization_id,role)"
                    + " VALUES(?,?,'MANAGER')",
                user,
                organization);
        sql(
                "INSERT dbo.tc_organization_memberships(user_id,organization_id,role)"
                    + " VALUES(?,?,'MANAGER')",
                keeper,
                organization);
        service = new EventService(new EventRepository(source), storage);
    }

    EventFields fields(String title) {
        Instant t = Instant.parse("2030-01-01T00:00:00Z");
        return new EventFields(
                category,
                title,
                "Fixture description",
                "Fixture venue",
                "Fixture address",
                t,
                t.plusSeconds(3600),
                t.plusSeconds(7200),
                t.plusSeconds(10800));
    }

    EventDto create() {
        return service.save(
                actor,
                new EventSave(
                        UUID.randomUUID(),
                        EventSave.Kind.CREATE,
                        organization,
                        0,
                        fields("Created"),
                        png(0x112233)));
    }

    EventSave edit(EventDto e, String title, byte[] bytes) {
        return new EventSave(
                UUID.randomUUID(), EventSave.Kind.EDIT, e.id(), e.version(), fields(title), bytes);
    }

    static byte[] png(int color) {
        try {
            BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, color);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            ImageIO.write(image, "png", b);
            return b.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    void sql(String sql, Object... args) throws SQLException {
        try (Connection c = source.getConnection();
                PreparedStatement q = c.prepareStatement(sql)) {
            bind(q, args);
            q.executeUpdate();
        }
    }

    long count(String sql, Object... args) throws SQLException {
        try (Connection c = source.getConnection();
                PreparedStatement q = c.prepareStatement(sql)) {
            bind(q, args);
            try (ResultSet r = q.executeQuery()) {
                r.next();
                return r.getLong(1);
            }
        }
    }

    private static void bind(PreparedStatement q, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++)
            q.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
    }

    @Override
    public void close() throws SQLException {
        sql(
                "DELETE dbo.tc_event_image_assets WHERE public_id IN (SELECT public_id FROM"
                    + " dbo.tc_event_image_requests WHERE organization_id IN (?,?))",
                organization,
                foreign);
        sql(
                "DELETE dbo.tc_event_image_requests WHERE organization_id IN (?,?)",
                organization,
                foreign);
        sql(
                "DELETE dbo.tc_event_image_assets WHERE event_id IN (SELECT id FROM dbo.tc_events"
                    + " WHERE organization_id IN (?,?))",
                organization,
                foreign);
        sql(
                "DELETE dbo.tc_event_image_assets WHERE public_id LIKE ?",
                "ticketcenter/events/%/fixture-" + user + "%");
        sql("DELETE dbo.tc_events WHERE organization_id IN (?,?)", organization, foreign);
        sql(
                "DISABLE TRIGGER dbo.trg_Membership_ProtectLastManager ON"
                    + " dbo.tc_organization_memberships");
        sql(
                "DISABLE TRIGGER dbo.trg_UserOrganizationRole_ProtectLastManager ON"
                    + " dbo.tc_organization_memberships");
        try {
            sql("DELETE dbo.tc_organization_memberships WHERE user_id IN (?,?)", user, keeper);
        } finally {
            sql(
                    "ENABLE TRIGGER dbo.trg_Membership_ProtectLastManager ON"
                        + " dbo.tc_organization_memberships");
            sql(
                    "ENABLE TRIGGER dbo.trg_UserOrganizationRole_ProtectLastManager ON"
                        + " dbo.tc_organization_memberships");
        }
        sql("DELETE dbo.tc_organizations WHERE id IN (?,?)", organization, foreign);
        sql("DELETE dbo.tc_event_categories WHERE id=?", category);
        sql("DISABLE TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs");
        try {
            sql("DELETE dbo.tc_audit_logs WHERE actor_id IN (?,?)", user, keeper);
        } finally {
            sql("ENABLE TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs");
        }
        sql("DELETE dbo.tc_users WHERE id IN (?,?)", user, keeper);
    }

    static final class FakeStorage implements StorageService {
        final Map<String, StoredAsset> assets = new ConcurrentHashMap<>();
        final List<String> deleted = Collections.synchronizedList(new ArrayList<>());
        volatile boolean fail;
        volatile Runnable uploaded = () -> {};

        public StoredAsset upload(InputStream input, String folder) throws IOException {
            return ensure(input, folder + "/" + UUID.randomUUID());
        }

        public StoredAsset ensure(InputStream input, String id) throws IOException {
            input.readAllBytes();
            if (fail) throw new IOException("Injected provider failure");
            StoredAsset asset =
                    assets.computeIfAbsent(
                            id,
                            k ->
                                    new StoredAsset(
                                            "https://res.cloudinary.com/fixture/image/upload/"
                                                    + k
                                                    + ".png",
                                            k));
            uploaded.run();
            return asset;
        }

        public Optional<StoredAsset> find(String id) {
            return Optional.ofNullable(assets.get(id));
        }

        public void delete(String id) {
            deleted.add(id);
            assets.remove(id);
        }

        public String getUrl(String id) {
            return "https://res.cloudinary.com/fixture/image/upload/" + id + ".png";
        }
    }

    static final class Source implements DataSource {
        final AtomicBoolean loseNextCommit = new AtomicBoolean();

        public Connection getConnection() throws SQLException {
            String url =
                    Objects.requireNonNull(
                            System.getenv("LIEM43_TEST_JDBC"), "LIEM43_TEST_JDBC required");
            if (!url.startsWith("jdbc:sqlserver://127.0.0.1:")
                    || !url.contains("databaseName=" + DATABASE + ";"))
                throw new IllegalStateException("Refusing non-isolated SQL target");
            Connection c =
                    DriverManager.getConnection(
                            url,
                            System.getenv("LIEM43_TEST_USER"),
                            System.getenv("LIEM43_TEST_PASSWORD"));
            return (Connection)
                    Proxy.newProxyInstance(
                            Connection.class.getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) -> {
                                try {
                                    Object result = m.invoke(c, a);
                                    if (m.getName().equals("commit")
                                            && loseNextCommit.compareAndSet(true, false))
                                        throw new SQLException(
                                                "Injected lost commit acknowledgement");
                                    return result;
                                } catch (InvocationTargetException e) {
                                    throw e.getCause();
                                }
                            });
        }

        public Connection getConnection(String u, String p) throws SQLException {
            return getConnection();
        }

        public PrintWriter getLogWriter() {
            return null;
        }

        public void setLogWriter(PrintWriter p) {}

        public void setLoginTimeout(int s) {}

        public int getLoginTimeout() {
            return 0;
        }

        public Logger getParentLogger() {
            return Logger.getLogger("fixture");
        }

        public <T> T unwrap(Class<T> type) throws SQLException {
            throw new SQLException("Not a wrapper");
        }

        public boolean isWrapperFor(Class<?> type) {
            return false;
        }
    }
}
