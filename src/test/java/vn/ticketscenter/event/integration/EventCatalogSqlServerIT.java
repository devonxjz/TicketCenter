package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.AppConfig;
import vn.ticketscenter.config.DatabaseConfig;
import vn.ticketscenter.event.cache.EventCatalogCache;
import vn.ticketscenter.event.dto.EventFilter;
import vn.ticketscenter.event.dao.EventRepositoryFixture;
import vn.ticketscenter.event.service.EventCatalogService;
import vn.ticketscenter.event.service.EventCatalogServiceImpl;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Requires an existing, dedicated SQL Server fixture; performs no schema/data writes and never skips. */
class EventCatalogSqlServerIT {
    @Test void publicVisibilityStablePaginationAndCacheHitUseActualSqlServer() throws Exception {
        String database = required("TC_SQL_TEST_DB");
        assertTrue(database.matches("[A-Za-z0-9_]+"), "Dedicated test database name must be a simple identifier");
        UUID published = EventFilter.id(required("TC_EVENT_TEST_PUBLISHED_ID"));
        UUID hidden = EventFilter.id(required("TC_EVENT_TEST_HIDDEN_ID"));
        UUID zone = EventFilter.id(required("TC_EVENT_TEST_ZONE_ID"));
        String url = "jdbc:sqlserver://" + DatabaseConfig.getHost() + ":" + DatabaseConfig.getPort()
                + ";databaseName=" + database + ";encrypt=true;trustServerCertificate="
                + DatabaseConfig.isTrustServerCertificate() + ";loginTimeout=5";
        try (Connection sql = DriverManager.getConnection(url, required("TC_SQL_TEST_LOGIN"), required("TC_SQL_TEST_PASSWORD"))) {
            sql.setAutoCommit(false);
            try (var query = sql.prepareStatement("SELECT IS_ROLEMEMBER('tc_buyer'), IS_SRVROLEMEMBER('sysadmin')")) {
                query.setQueryTimeout(5);
                try (var role = query.executeQuery()) {
                    assertTrue(role.next()); assertEquals(1, role.getInt(1), "Use a dedicated tc_buyer login");
                    assertEquals(0, role.getInt(2), "A sysadmin login cannot prove runtime permissions");
                }
            }
            AtomicInteger queries = new AtomicInteger();
            Connection counted = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) queries.incrementAndGet();
                        try { return method.invoke(sql, args); } catch (InvocationTargetException e) { throw e.getCause(); }
                    });
            var repository = EventRepositoryFixture.repository(counted);
            assertTrue(repository.event(published).isPresent()); assertTrue(repository.event(hidden).isEmpty());
            assertTrue(repository.zones(hidden).isEmpty());
            assertTrue(repository.zones(published).orElseThrow().stream().anyMatch(z -> z.zoneId().equals(zone)));
            // Run under tc_buyer: this intentionally fails until the database owner grants the public seat projection.
            assertFalse(repository.seats(zone).orElseThrow().isEmpty(), "Provision at least one seat in the public seated Zone");
            var firstFilter = EventFilter.parse(Map.of("pageSize", "1"));
            var first = repository.events(firstFilter);
            assertEquals(first, repository.events(firstFilter));
            assertFalse(first.events().stream().anyMatch(e -> e.eventId().equals(hidden)));
            var second = repository.events(EventFilter.parse(Map.of("pageSize", "1", "page", "2")));
            assertEquals(first.total(), second.total());
            if (!second.events().isEmpty()) assertNotEquals(first.events().getFirst().eventId(), second.events().getFirst().eventId());
            Map<String, String> values = new HashMap<>();
            var cache = new EventCatalogCache() {
                public Optional<String> get(String key) { return Optional.ofNullable(values.get(key)); }
                public void put(String key, String json, int ttl) { values.put(key, json); }
            };
            var service = new EventCatalogServiceImpl(repository, cache);
            String json = service.events(firstFilter); int beforeHit = queries.get();
            assertEquals(json, service.events(firstFilter)); assertEquals(beforeHit, queries.get(), "Warm cache must avoid SQL queries");
            var uncached = new EventCatalogServiceImpl(repository, new EventCatalogCache() {
                public Optional<String> get(String key) { throw new IllegalStateException("Redis unavailable"); }
                public void put(String key, String value, int ttl) { throw new IllegalStateException("Redis unavailable"); }
            });
            assertEquals(json, uncached.events(firstFilter)); assertTrue(queries.get() > beforeHit);
            sql.rollback();
        }
    }
    private static String required(String key) {
        String value = AppConfig.get(key);
        assertNotNull(value, "Required dedicated integration setting: " + key);
        assertFalse(value.isBlank(), "Required dedicated integration setting: " + key);
        return value;
    }
}
