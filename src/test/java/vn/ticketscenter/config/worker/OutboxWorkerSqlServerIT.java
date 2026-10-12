package vn.ticketscenter.config.worker;

import org.junit.jupiter.api.*;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.config.persistence.PersistenceRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in (dedicated loopback fixture DB, see ExternalTestCondition); requires migrations through 049. */
@org.junit.jupiter.api.extension.ExtendWith(vn.ticketscenter.platform.testsupport.ExternalTestCondition.class)
class OutboxWorkerSqlServerIT {
    private static PersistenceRegistry registry;
    private final List<UUID> ids = new ArrayList<>();

    @BeforeAll static void start() {
        registry = new PersistenceRegistry();
        registry.initialize();
    }
    @AfterAll static void stop() { if (registry != null) registry.close(); }
    @AfterEach void cleanup() throws SQLException {
        try (Connection c = admin(); PreparedStatement ps = c.prepareStatement("DELETE dbo.tc_outbox WHERE id=?")) {
            for (UUID id : ids) { ps.setObject(1, id); ps.executeUpdate(); }
        }
    }

    private static Connection admin() throws SQLException {
        return DriverManager.getConnection(DatabaseConfig.getJdbcUrl(), DatabaseConfig.getUser(), DatabaseConfig.getPassword());
    }

    private UUID insert(String type, int attempts) throws SQLException {
        UUID id = UUID.randomUUID();
        ids.add(id);
        try (Connection c = admin(); PreparedStatement ps = c.prepareStatement(
                "INSERT dbo.tc_outbox(id,event_type,aggregate_type,aggregate_id,payload,idempotency_key,attempts) VALUES(?,?,'TEST',?,'{}',?,?)")) {
            ps.setObject(1, id); ps.setString(2, type); ps.setObject(3, UUID.randomUUID());
            ps.setString(4, "it:" + id); ps.setInt(5, attempts);
            ps.executeUpdate();
        }
        return id;
    }

    private String status(UUID id) throws SQLException {
        try (Connection c = admin(); PreparedStatement ps = c.prepareStatement("SELECT status FROM dbo.tc_outbox WHERE id=?")) {
            ps.setObject(1, id);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getString(1); }
        }
    }

    @Test void successfulDeliveryIsPublished() throws Exception {
        UUID id = insert("IT_OK", 0);
        new OutboxWorker(registry.transactionManager(), (i, t, p) -> { }, "it-1").runOnce();
        assertEquals("PUBLISHED", status(id));
    }

    @Test void failedDeliveryIsMarkedFailedAndCappedAtMaxAttempts() throws Exception {
        UUID id = insert("IT_FAIL", 0);
        new OutboxWorker(registry.transactionManager(), (i, t, p) -> { throw new IllegalStateException("smtp"); }, "it-1").runOnce();
        assertEquals("FAILED", status(id));
        UUID capped = insert("IT_CAPPED", OutboxWorker.MAX_ATTEMPTS);
        Set<UUID> delivered = ConcurrentHashMap.newKeySet();
        new OutboxWorker(registry.transactionManager(), (i, t, p) -> delivered.add(i), "it-1").runOnce();
        assertEquals("PENDING", status(capped));
        assertFalse(delivered.contains(capped));
    }

    @Test void expiredLeaseIsReclaimed() throws Exception {
        UUID id = insert("IT_LEASE", 1);
        try (Connection c = admin(); Statement s = c.createStatement()) {
            s.executeUpdate("UPDATE dbo.tc_outbox SET status='PROCESSING',lease_owner='dead',lease_until=DATEADD(minute,-1,SYSUTCDATETIME()) WHERE id='" + id + "'");
        }
        new OutboxWorker(registry.transactionManager(), (i, t, p) -> { }, "it-2").runOnce();
        assertEquals("PUBLISHED", status(id));
    }

    @Test void parallelWorkersNeverDeliverTheSameRowTwice() throws Exception {
        List<UUID> rows = new ArrayList<>();
        for (int i = 0; i < 20; i++) rows.add(insert("IT_PARALLEL", 0));
        Map<UUID, AtomicInteger> deliveries = new ConcurrentHashMap<>();
        OutboxWorker.EmailJob job = (i, t, p) -> deliveries.computeIfAbsent(i, k -> new AtomicInteger()).incrementAndGet();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> runs = List.of(
                pool.submit(() -> new OutboxWorker(registry.transactionManager(), job, "w1").runOnce()),
                pool.submit(() -> new OutboxWorker(registry.transactionManager(), job, "w2").runOnce()));
        for (Future<Integer> run : runs) run.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        for (UUID id : rows) assertTrue(deliveries.getOrDefault(id, new AtomicInteger()).get() <= 1, "duplicate delivery " + id);
    }

    @Test void workerViewsExist() throws Exception {
        try (Connection c = admin(); Statement s = c.createStatement()) {
            s.executeQuery("SELECT TOP 1 * FROM dbo.vw_OutboxEmailContext").close();
            s.executeQuery("SELECT TOP 1 id,order_id,txn_ref,amount,created_at FROM dbo.vw_PaymentsToReconcile").close();
        }
    }
}
