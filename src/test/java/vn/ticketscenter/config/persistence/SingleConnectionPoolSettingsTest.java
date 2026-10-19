package vn.ticketscenter.config.persistence;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.fulfillment.ticket.service.TicketRuntime;
import vn.ticketscenter.order.coupon.service.CouponAdminRuntime;
import vn.ticketscenter.order.service.OrderRuntime;
import vn.ticketscenter.payment.service.PaymentRuntime;
import vn.ticketscenter.ticketing.service.HoldRuntime;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class SingleConnectionPoolSettingsTest {
    static final String URL = "jdbc:sqlserver://127.0.0.1:1;databaseName=pin;encrypt=false;loginTimeout=1;";

    private static void withRuntime(ServletContextListener runtime, Consumer<Map<String, Object>> body, java.util.function.Consumer<ServletContext> resolve) {
        Map<String, Object> attributes = new ConcurrentHashMap<>();
        ServletContext context = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(), new Class<?>[] {ServletContext.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> attributes.get(args[0]);
                    case "setAttribute" -> { attributes.put((String) args[0], args[1]); yield null; }
                    case "removeAttribute" -> { attributes.remove(args[0]); yield null; }
                    default -> null;
                });
        Map<String, String> properties = new HashMap<>();
        properties.put("db.url", URL);
        for (String principal : List.of("buyer", "worker", "manager", "admin")) {
            properties.put("db." + principal + ".user", principal + "-user");
            properties.put("db." + principal + ".password", principal + "-secret");
        }
        properties.put("redis.host", "127.0.0.1");
        properties.put("redis.port", "1");
        properties.put("finance.redis.enabled", "false");
        properties.put("app.secret.key", "pin-test-secret-key-with-32-bytes-minimum");
        properties.put("payos.client.id", "pin-client");
        properties.put("payos.api.key", "pin-api");
        properties.put("payos.checksum.key", "pin-checksum");
        Map<String, String> original = new HashMap<>();
        properties.forEach((key, value) -> { original.put(key, System.getProperty(key)); System.setProperty(key, value); });
        var event = new ServletContextEvent(context);
        try {
            runtime.contextInitialized(event);
            resolve.accept(context);
            body.accept(attributes);
        } finally {
            runtime.contextDestroyed(event);
            RedisClientProvider.getInstance().close();
            original.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }

    private static void assertSingleConnection(Object pool, String name, String principal) {
        HikariDataSource source = (HikariDataSource) pool;
        assertEquals(name, source.getPoolName());
        assertEquals(URL, source.getJdbcUrl());
        assertEquals(principal + "-user", source.getUsername());
        assertEquals(principal + "-secret", source.getPassword());
        assertEquals(1, source.getMaximumPoolSize());
        assertEquals(0, source.getMinimumIdle());
        assertEquals(-1, source.getInitializationFailTimeout());
        assertEquals(10_000, source.getConnectionTimeout());
        assertTrue(source.isAutoCommit());
    }

    @Test void orderRuntimePool() {
        withRuntime(new OrderRuntime(), attributes -> assertSingleConnection(attributes.get(OrderRuntime.class.getName() + ".pool"), "order-buyer", "buyer"), OrderRuntime::resolve);
    }

    @Test void ticketRuntimePool() {
        withRuntime(new TicketRuntime(), attributes -> assertSingleConnection(attributes.get(TicketRuntime.class.getName() + ".pool"), "ticket-buyer", "buyer"), TicketRuntime::resolve);
    }

    @Test void holdRuntimePools() {
        withRuntime(new HoldRuntime(), attributes -> {
            List<?> pools = (List<?>) attributes.get(HoldRuntime.class.getName() + ".pools");
            assertEquals(2, pools.size());
            assertSingleConnection(pools.get(0), "hold-buyer", "buyer");
            assertSingleConnection(pools.get(1), "hold-worker", "worker");
        }, HoldRuntime::resolve);
    }

    @Test void couponAdminRuntimePools() {
        withRuntime(new CouponAdminRuntime(), attributes -> {
            assertSingleConnection(attributes.get(CouponAdminRuntime.class.getName() + ".managerPool"), "coupon-manager", "manager");
            assertSingleConnection(attributes.get(CouponAdminRuntime.class.getName() + ".adminPool"), "coupon-admin", "admin");
        }, CouponAdminRuntime::resolve);
    }

    @Test void paymentRuntimePools() {
        withRuntime(new PaymentRuntime(), attributes -> {
            Object components = attributes.get(PaymentRuntime.class.getName() + ".components");
            assertNotNull(components);
            assertSingleConnection(component(components, "buyerPool"), "payment-buyer", "buyer");
            assertSingleConnection(component(components, "workerPool"), "payment-worker", "worker");
        }, PaymentRuntime::resolve);
    }

    private static Object component(Object record, String name) {
        try {
            var accessor = record.getClass().getDeclaredMethod(name);
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}
