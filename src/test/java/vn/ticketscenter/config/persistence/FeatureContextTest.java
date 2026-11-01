package vn.ticketscenter.config.persistence;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class FeatureContextTest {
    private static final String KEY = "feature";

    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final ServletContext context =
            (ServletContext)
                    Proxy.newProxyInstance(
                            ServletContext.class.getClassLoader(),
                            new Class<?>[] {ServletContext.class},
                            (proxy, method, args) ->
                                    switch (method.getName()) {
                                        case "getAttribute" -> attributes.get(args[0]);
                                        case "setAttribute" -> attributes.put((String) args[0], args[1]);
                                        case "removeAttribute" -> attributes.remove(args[0]);
                                        default -> null;
                                    });

    @Test
    void buildsOnceAndClosesWhatItOpenedWhenTheApplicationStops() {
        List<String> closed = new ArrayList<>();
        int[] builds = {0};
        FeatureContext.open(context, KEY);

        String first = resolve(resources -> {
            builds[0]++;
            resources.track(() -> closed.add("pool"));
            return "service";
        });
        String second = resolve(resources -> "other");

        assertEquals("service", first);
        assertSame(first, second);
        assertEquals(1, builds[0]);
        assertEquals(1, FeatureContext.resources(context, KEY).size());

        FeatureContext.close(context, KEY);

        assertEquals(List.of("pool"), closed);
        assertNull(attributes.get(KEY));
        assertTrue(FeatureContext.resources(context, KEY).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> resolve(resources -> "again"));
    }

    @Test
    void failedBuildClosesOpenedResourcesInReverseAndMapsTheFailure() {
        List<String> closed = new ArrayList<>();

        var failure =
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> resolve(resources -> {
                            resources.track(() -> closed.add("buyer"));
                            resources.track(() -> closed.add("worker"));
                            throw new IllegalStateException("redis down");
                        }));

        assertEquals("redis down", failure.getMessage());
        assertEquals(List.of("worker", "buyer"), closed);
        assertNull(attributes.get(KEY));
    }

    @Test
    void cachedValueIsStoredOnlyAfterItWasBuilt() {
        assertThrows(
                IllegalStateException.class,
                () -> FeatureContext.cached(context, KEY, () -> {
                    throw new IllegalStateException("not ready");
                }));
        assertNull(attributes.get(KEY));

        assertEquals("ready", FeatureContext.cached(context, KEY, () -> "ready"));
        assertEquals("ready", FeatureContext.cached(context, KEY, () -> "rebuilt"));
    }

    @Test
    void registryIsUnavailableUntilPersistenceStarted() {
        assertTrue(FeatureContext.registry(context).isEmpty());
        assertThrows(
                UnsupportedOperationException.class,
                () -> FeatureContext.registry(context, null, UnsupportedOperationException::new));
    }

    private String resolve(java.util.function.Function<FeatureContext.Resources, String> builder) {
        return FeatureContext.resolve(
                context, KEY, builder, failure -> new UnsupportedOperationException(failure.getMessage()));
    }
}
