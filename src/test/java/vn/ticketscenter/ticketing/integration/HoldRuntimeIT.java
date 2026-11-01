package vn.ticketscenter.ticketing.integration;

import vn.ticketscenter.config.persistence.FeatureContext;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.ticketing.service.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class HoldRuntimeIT {
    @Test void startsExpiryWithoutBuyerRequestAndClosesItsPools() throws Exception {
        try (HoldFixture fixture = new HoldFixture()) {
            UUID hold = HoldFixture.holdId(fixture.mutate(fixture.actor, fixture.standing(2)));
            fixture.expire(hold);
            Map<String, Object> attributes = new ConcurrentHashMap<>();
            ServletContext context = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(),
                    new Class<?>[]{ServletContext.class}, (p, method, args) -> switch (method.getName()) {
                        case "getAttribute" -> attributes.get(args[0]);
                        case "setAttribute" -> { attributes.put((String)args[0], args[1]); yield null; }
                        case "removeAttribute" -> { attributes.remove(args[0]); yield null; }
                        case "getAttributeNames" -> Collections.enumeration(attributes.keySet());
                        default -> null;
                    });
            Map<String, String> properties = new LinkedHashMap<>();
            properties.put("db.url", System.getenv("LIEM01_TEST_JDBC"));
            properties.put("db.buyer.user", System.getenv("LIEM01_TEST_BUYER_USER"));
            properties.put("db.buyer.password", System.getenv("LIEM01_TEST_BUYER_PASSWORD"));
            properties.put("db.worker.user", System.getenv("LIEM01_TEST_WORKER_USER"));
            properties.put("db.worker.password", System.getenv("LIEM01_TEST_WORKER_PASSWORD"));
            properties.put("redis.host", "127.0.0.1");
            properties.put("redis.port", System.getProperty("liem.hold.redisTestPort"));
            Map<String, String> original = new HashMap<>();
            properties.forEach((key, value) -> { original.put(key, System.getProperty(key)); System.setProperty(key, value); });
            HoldRuntime runtime = new HoldRuntime();
            ServletContextEvent event = new ServletContextEvent(context);
            List<?> pools = List.of();
            try {
                runtime.contextInitialized(event);
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
                while (fixture.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND status='RELEASED'", hold) == 0
                        && System.nanoTime() < deadline) Thread.sleep(50);
                assertEquals(1, fixture.count("SELECT COUNT(*) FROM dbo.tc_ticket_holds WHERE id=? AND status='RELEASED'", hold));
                assertInstanceOf(HoldService.class, context.getAttribute(HoldRuntime.SERVICE_ATTRIBUTE));
                pools = FeatureContext.resources(context, HoldRuntime.SERVICE_ATTRIBUTE);
                assertEquals(2, pools.size());
            } finally {
                runtime.contextDestroyed(event);
                RedisClientProvider.getInstance().close();
                original.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
            }
            for (Object pool : pools) assertTrue(((HikariDataSource)pool).isClosed());
            assertNull(context.getAttribute(HoldRuntime.SERVICE_ATTRIBUTE));
            assertThrows(HoldException.class, () -> HoldRuntime.resolve(context));
        }
    }
}
