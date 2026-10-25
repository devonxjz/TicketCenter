package vn.ticketscenter.order.integration;

import org.json.JSONObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import vn.ticketscenter.ticketing.cache.*;
import vn.ticketscenter.ticketing.dto.*;
import vn.ticketscenter.ticketing.dao.HoldRepository;
import vn.ticketscenter.ticketing.service.HoldService;
import vn.ticketscenter.ticketing.service.HoldServiceImpl;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

final class OrderFixture implements AutoCloseable {
    static final String DATABASE = "TicketCenter_LIEM02_Test";
    final UUID organization = UUID.randomUUID(), category = UUID.randomUUID();
    final List<UUID> actors = new ArrayList<>();
    final List<UUID> events = new ArrayList<>();
    final Set<String> redisKeys = Collections.synchronizedSet(new HashSet<>());
    final Source owner = new Source("OWNER");
    final Source buyer = new Source("BUYER");
    final Source worker = new Source("WORKER");
    final HoldRepository repository = new HoldRepository(buyer, worker);
    final java.util.List<UUID> coupons = new java.util.ArrayList<>();
    final vn.ticketscenter.order.dao.OrderRepository orders = new vn.ticketscenter.order.dao.OrderRepository(buyer);
    final vn.ticketscenter.order.cache.RedisOrderCache orderCache;
    final vn.ticketscenter.order.service.OrderService orderService;
    final HoldActor actor;
    final Inventory inventory;
    final JedisPool redis;
    final RedisActiveHoldCache cache;
    final HoldService service;

    OrderFixture() throws SQLException {
        String host = System.getProperty("liem.order.redisTestHost");
        String port = System.getProperty("liem.order.redisTestPort");
        if (!"127.0.0.1".equals(host) || port == null || !port.matches("[0-9]{1,5}")
                || Integer.parseInt(port) < 1 || Integer.parseInt(port) > 65535)
            throw new IllegalStateException("Explicit loopback Redis fixture required");
        JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(12);
        redis = new JedisPool(config, host, Integer.parseInt(port), 1000);
        try (Jedis jedis = redis.getResource()) {
            if (!"PONG".equals(jedis.ping())) throw new IllegalStateException("Redis fixture unavailable");
        }
        cache = new RedisActiveHoldCache(redis::getResource, Clock.systemUTC());
        service = new HoldServiceImpl(repository, cache, new RedisHoldIdempotency(redis::getResource));
        sql("INSERT dbo.tc_organizations(id,name,contact_email,status) VALUES(?,'Hold fixture',?,'APPROVED')",
                organization, organization + "@example.test");
        sql("INSERT dbo.tc_event_categories(id,name,slug) VALUES(?,'Hold fixture',?)", category, category.toString());
        orderCache = new vn.ticketscenter.order.cache.RedisOrderCache(redis::getResource, Clock.systemUTC());
        orderService = new vn.ticketscenter.order.service.OrderServiceImpl(orders, orderCache);
        actor = actor();
        inventory = inventory(8);
    }

    HoldActor actor() throws SQLException {
        UUID id = UUID.randomUUID();
        sql("INSERT dbo.tc_users(id,email,password_hash,full_name,user_name,email_verified_at)"
                + " VALUES(?,?,'fixture-only','Hold fixture',?,SYSUTCDATETIME())", id, id + "@example.test", "hold-" + id);
        actors.add(id);
        redisKeys.add(RedisActiveHoldCache.redisKey(id));
        return new HoldActor(id, 0);
    }

    record Inventory(UUID event, UUID standing, UUID seated, UUID seat) {}

    Inventory inventory(int capacity) throws SQLException {
        UUID event = UUID.randomUUID(), standing = UUID.randomUUID(), seated = UUID.randomUUID(), seat = UUID.randomUUID();
        sql("INSERT dbo.tc_events(id,organization_id,category_id,title,description,venue_name,venue_address,"
                + "sale_start,sale_end,start_time,end_time,status) VALUES(?,?,?,'Hold fixture','Fixture','Venue','Address',"
                + "DATEADD(day,-1,SYSUTCDATETIME()),DATEADD(day,1,SYSUTCDATETIME()),DATEADD(day,2,SYSUTCDATETIME()),"
                + "DATEADD(day,3,SYSUTCDATETIME()),'DRAFT')", event, organization, category);
        events.add(event);
        sql("INSERT dbo.tc_zones(id,event_id,name,type,price,capacity) VALUES(?,?,'Floor','STANDING',150000,?)",
                standing, event, capacity);
        sql("INSERT dbo.tc_zones(id,event_id,name,type,price) VALUES(?,?,'Balcony','SEATED',200000)", seated, event);
        sql("INSERT dbo.tc_seats(id,zone_id,row_name,seat_number,label) VALUES(?,?,'A',1,'A1')", seat, seated);
        sql("UPDATE dbo.tc_events SET status='PUBLISHED' WHERE id=?", event);
        return new Inventory(event, standing, seated, seat);
    }

    HoldCommand.Create standing(int quantity) { return standing(UUID.randomUUID(), inventory, quantity); }
    HoldCommand.Create standing(UUID key, Inventory stock, int quantity) {
        return new HoldCommand.Create(key, new CreateHoldRequest(stock.event(), List.of(new HoldSelection(stock.standing(), null, quantity))));
    }
    HoldCommand.Create seat() {
        return new HoldCommand.Create(UUID.randomUUID(), new CreateHoldRequest(inventory.event(),
                List.of(new HoldSelection(inventory.seated(), inventory.seat(), 1))));
    }

    HoldMutationReceipt mutate(HoldActor user, HoldCommand command) {
        redisKeys.add(RedisHoldIdempotency.redisKey(user.id(), command.key().toString()));
        return service.mutate(user, command);
    }

    static UUID holdId(HoldMutationReceipt receipt) { return UUID.fromString(new JSONObject(receipt.body()).getString("id")); }

    void expire(UUID hold) throws SQLException {
        sql("UPDATE dbo.tc_ticket_holds SET created_at=DATEADD(second,-1200,SYSUTCDATETIME()),"
                + "expires_at=DATEADD(second,-1,SYSUTCDATETIME()) WHERE id=?", hold);
    }

    void sql(String text, Object... args) throws SQLException {
        try (Connection connection = owner.getConnection(); PreparedStatement query = connection.prepareStatement(text)) {
            query.setQueryTimeout(20);
            bind(query, args);
            query.executeUpdate();
        }
    }

    long count(String text, Object... args) throws SQLException {
        try (Connection connection = owner.getConnection(); PreparedStatement query = connection.prepareStatement(text)) {
            query.setQueryTimeout(20);
            bind(query, args);
            try (ResultSet rows = query.executeQuery()) { rows.next(); return rows.getLong(1); }
        }
    }

    private static void bind(PreparedStatement query, Object[] args) throws SQLException {
        for (int i = 0; i < args.length; i++) query.setObject(i + 1, args[i] instanceof UUID id ? id.toString() : args[i]);
    }

    @Override
    public void close() throws SQLException {
        try {
            for (UUID event : events) sql("UPDATE dbo.tc_events SET status='DRAFT' WHERE id=?", event);
            try (Connection connection = owner.getConnection()) {
                connection.setAutoCommit(false);
                try (Statement query = connection.createStatement()) {
                    query.executeUpdate("DISABLE TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs");
                    for (UUID user : actors) {
                        try (PreparedStatement cleanup = connection.prepareStatement(
                                "DELETE dbo.tc_audit_logs WHERE actor_id=? OR aggregate_id IN(SELECT id FROM dbo.tc_ticket_holds WHERE user_id=?)"
                                        + " OR aggregate_id IN(SELECT id FROM dbo.tc_orders WHERE user_id=?)"
                                        + " OR aggregate_id IN(SELECT p.id FROM dbo.tc_payments p JOIN dbo.tc_orders o ON o.id=p.order_id WHERE o.user_id=?)")) {
                            for (int i = 1; i <= 4; i++) cleanup.setString(i, user.toString());
                            cleanup.executeUpdate();
                        }
                    }
                    for (UUID event : events) try (PreparedStatement cleanup = connection.prepareStatement("DELETE dbo.tc_audit_logs WHERE aggregate_id=?")) {
                        cleanup.setString(1, event.toString()); cleanup.executeUpdate();
                    }
                    query.executeUpdate("ENABLE TRIGGER dbo.trg_AuditLog_AppendOnly ON dbo.tc_audit_logs");
                    connection.commit();
                } catch (SQLException failure) { connection.rollback(); throw failure; }
            }
            for (UUID user : actors) {
                try (Connection connection = owner.getConnection(); PreparedStatement query = connection.prepareStatement("SELECT id FROM dbo.tc_orders WHERE user_id=?")) {
                    query.setString(1,user.toString()); try (ResultSet rows=query.executeQuery()) { while(rows.next()) redisKeys.add("tc:order:summary:" + rows.getString(1).toLowerCase(java.util.Locale.ROOT)); }
                }
                sql("DELETE dbo.tc_request_idempotency WHERE actor_id=?", user);
                sql("UPDATE dbo.tc_orders SET accepted_payment_id=NULL WHERE user_id=?", user);
                sql("DELETE dbo.tc_coupon_redemptions WHERE order_id IN(SELECT id FROM dbo.tc_orders WHERE user_id=?)", user);
                sql("DELETE dbo.tc_payments WHERE order_id IN(SELECT id FROM dbo.tc_orders WHERE user_id=?)", user);
                sql("DELETE dbo.tc_order_items WHERE order_id IN(SELECT id FROM dbo.tc_orders WHERE user_id=?)", user);
                sql("DELETE dbo.tc_orders WHERE user_id=?", user);
                sql("DELETE dbo.tc_ticket_hold_items WHERE hold_id IN(SELECT id FROM dbo.tc_ticket_holds WHERE user_id=?)", user);
                sql("DELETE dbo.tc_ticket_holds WHERE user_id=?", user);
                sql("DELETE dbo.tc_users WHERE id=?", user);
            }
            for (UUID event : events) {
                sql("DELETE dbo.tc_seats WHERE zone_id IN(SELECT id FROM dbo.tc_zones WHERE event_id=?)", event);
                sql("DELETE dbo.tc_zones WHERE event_id=?", event);
                sql("DELETE dbo.tc_events WHERE id=?", event);
            }
            for (UUID coupon : coupons) sql("DELETE dbo.tc_coupons WHERE id=?", coupon);
            sql("DELETE dbo.tc_event_categories WHERE id=?", category);
            sql("DELETE dbo.tc_organizations WHERE id=?", organization);
        } finally {
            try (redis) {
                try (Jedis jedis = redis.getResource()) { for (UUID user : actors) {
                    for (String prefix : java.util.List.of("tc:idem:order:" + user + ":*", "tc:order:fence:" + user + ":*", "tc:order:checkout:" + user + ":*")) {
                        String cursor = "0";
                        do { var batch = jedis.scan(cursor, new redis.clients.jedis.params.ScanParams().match(prefix));
                            if (!batch.getResult().isEmpty()) jedis.del(batch.getResult().toArray(String[]::new)); cursor = batch.getCursor();
                        } while (!cursor.equals("0"));
                    }
                }
                if (!redisKeys.isEmpty()) jedis.del(redisKeys.toArray(String[]::new)); }
            }
        }
    }

    static final class Source implements DataSource {
        final String role;
        final AtomicBoolean loseNextAcknowledgement = new AtomicBoolean();
        final AtomicInteger deadlocks = new AtomicInteger();
        final AtomicInteger preflightChanges = new AtomicInteger();
        volatile boolean unavailable;
        volatile Runnable afterCurrentRead = () -> {};

        Source(String role) { this.role = role; }

        public Connection getConnection() throws SQLException {
            String url = Objects.requireNonNull(System.getenv("LIEM02_TEST_JDBC"), "LIEM02_TEST_JDBC required");
            if (!url.matches("jdbc:sqlserver://127\\.0\\.0\\.1:[0-9]{1,5};databaseName=TicketCenter_LIEM02_Test;encrypt=true;trustServerCertificate=true;loginTimeout=10;"))
                throw new IllegalStateException("Refusing non-isolated SQL target");
            if (unavailable) throw new SQLException("Injected SQL outage", "08001");
            Connection connection = DriverManager.getConnection(url,
                    Objects.requireNonNull(System.getenv("LIEM02_TEST_" + role + "_USER")),
                    Objects.requireNonNull(System.getenv("LIEM02_TEST_" + role + "_PASSWORD")));
            try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                    "SELECT DB_NAME(),IS_ROLEMEMBER('db_owner'),IS_ROLEMEMBER('tc_buyer'),IS_ROLEMEMBER('tc_worker')")) {
                rows.next();
                if (!DATABASE.equals(rows.getString(1)) || !role.equals("OWNER") && rows.getInt(2) != 0
                        || role.equals("BUYER") && rows.getInt(3) != 1 || role.equals("WORKER") && rows.getInt(4) != 1) {
                    connection.close();
                    throw new IllegalStateException("Refusing unexpected SQL principal or target");
                }
            }
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, method, args) -> {
                try {
                    Object result = method.invoke(connection, args);
                    if (method.getName().equals("prepareStatement")) {
                        String sql = args[0].toString();
                        PreparedStatement statement = (PreparedStatement) result;
                        return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (sp, sm, sa) -> {
                            try {
                                Object value = sm.invoke(statement, sa);
                                if (sm.getName().equals("executeQuery") && sql.startsWith("EXEC dbo.usp_ReadCheckout")) afterCurrentRead.run();
                                if (sm.getName().equals("executeQuery") && (sql.startsWith("EXEC dbo.usp_CreateOrderRequest") || sql.startsWith("EXEC dbo.usp_ApplyOrderCouponRequest")) && loseNextAcknowledgement.compareAndSet(true, false)) {
                                    ((ResultSet) value).close();
                                    throw new SQLException("Injected lost committed reply", "08S01");
                                }
                                return value;
                            } catch (InvocationTargetException failure) {
                                if (failure.getCause() instanceof SQLException error && error.getErrorCode() == 1205) deadlocks.incrementAndGet();
                                if (failure.getCause() instanceof SQLException error && error.getErrorCode() == 51309) preflightChanges.incrementAndGet();
                                throw failure.getCause();
                            }
                        });
                    }
                    return result;
                } catch (InvocationTargetException failure) { throw failure.getCause(); }
            });
        }

        public Connection getConnection(String user, String password) throws SQLException { return getConnection(); }
        public PrintWriter getLogWriter() { return null; }
        public void setLogWriter(PrintWriter writer) {}
        public void setLoginTimeout(int seconds) {}
        public int getLoginTimeout() { return 0; }
        public Logger getParentLogger() { return Logger.getLogger("hold-fixture"); }
        public <T> T unwrap(Class<T> type) throws SQLException { throw new SQLException("Not a wrapper"); }
        public boolean isWrapperFor(Class<?> type) { return false; }
    }
}
