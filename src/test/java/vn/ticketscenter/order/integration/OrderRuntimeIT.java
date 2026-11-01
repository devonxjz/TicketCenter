package vn.ticketscenter.order.integration;

import vn.ticketscenter.config.persistence.FeatureContext;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.order.dto.OrderActor;
import vn.ticketscenter.order.service.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class OrderRuntimeIT {
    @Test void ownsOneRestrictedPoolWithoutWorkerAndClosesIt() throws Exception {
        try (OrderFixture fixture = new OrderFixture()) {
            UUID hold = OrderFixture.holdId(fixture.mutate(fixture.actor, fixture.standing(1)));
            Map<String,Object> attributes = new ConcurrentHashMap<>();
            ServletContext context = (ServletContext) Proxy.newProxyInstance(ServletContext.class.getClassLoader(), new Class<?>[]{ServletContext.class}, (p,method,args) -> switch(method.getName()) {
                case "getAttribute" -> attributes.get(args[0]);
                case "setAttribute" -> { attributes.put((String)args[0],args[1]); yield null; }
                case "removeAttribute" -> { attributes.remove(args[0]); yield null; }
                default -> null;
            });
            Map<String,String> properties = new LinkedHashMap<>();
            properties.put("db.url",System.getenv("LIEM02_TEST_JDBC"));
            properties.put("db.buyer.user",System.getenv("LIEM02_TEST_BUYER_USER"));
            properties.put("db.buyer.password",System.getenv("LIEM02_TEST_BUYER_PASSWORD"));
            properties.put("redis.host","127.0.0.1"); properties.put("redis.port",System.getProperty("liem.order.redisTestPort"));
            Map<String,String> original = new HashMap<>();
            properties.forEach((key,value) -> { original.put(key,System.getProperty(key)); System.setProperty(key,value); });
            var runtime = new OrderRuntime(); var event = new ServletContextEvent(context); HikariDataSource pool = null;
            try {
                runtime.contextInitialized(event);
                var service = OrderRuntime.resolve(context);
                assertTrue(service.checkout(new OrderActor(fixture.actor.id(),0),hold).order().isEmpty());
                assertSame(service,OrderRuntime.resolve(context));
                pool = (HikariDataSource)FeatureContext.resources(context,OrderRuntime.SERVICE_ATTRIBUTE).getFirst();
                assertEquals(1,pool.getMaximumPoolSize()); assertEquals(0,pool.getMinimumIdle());
                assertFalse(attributes.keySet().stream().anyMatch(key -> key.contains("worker")));
            } finally {
                runtime.contextDestroyed(event); RedisClientProvider.getInstance().close();
                original.forEach((key,value) -> { if(value==null) System.clearProperty(key); else System.setProperty(key,value); });
            }
            assertNotNull(pool); assertTrue(pool.isClosed()); assertNull(context.getAttribute(OrderRuntime.SERVICE_ATTRIBUTE));
            assertEquals(503,assertThrows(OrderException.class,() -> OrderRuntime.resolve(context)).status());
        }
    }
}
