package vn.ticketscenter.platform.testsupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.engine.JupiterTestEngine;
import org.junit.platform.engine.*;
import org.junit.platform.engine.discovery.ClassSelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ExternalTestIsolationTest {
    private static final AtomicInteger setupCalls = new AtomicInteger();
    private static final AtomicInteger pureCalls = new AtomicInteger();

    @Test void defaultDbGateStopsBeforeAll() {
        setupCalls.set(0);
        ProbeResult result = execute(DbProbe.class, Map.of());
        assertEquals(0, setupCalls.get(), "DB setup must be disabled before @BeforeAll");
        assertEquals(1, result.skipped);
    }

    @Test void defaultSmtpGateStopsBeforeAll() {
        setupCalls.set(0);
        ProbeResult result = execute(SmtpProbe.class, Map.of());
        assertEquals(0, setupCalls.get(), "SMTP setup must be disabled before @BeforeAll");
        assertEquals(1, result.skipped);
    }

    @Test void mixedClassKeepsPureMethodsEnabled() {
        setupCalls.set(0);
        pureCalls.set(0);
        ProbeResult result = execute(MixedProbe.class, Map.of());
        assertEquals(0, setupCalls.get());
        assertEquals(1, pureCalls.get());
        assertEquals(1, result.skipped);
        assertEquals(0, result.failed);
    }

    @Test void explicitDbOptInWithoutTargetFailsBeforeSetup() {
        setupCalls.set(0);
        ProbeResult result = execute(DbProbe.class, Map.of("tc.tests.db", "true"));
        assertEquals(0, setupCalls.get());
        assertEquals(1, result.failed, "Missing opt-in target must fail, not silently skip");
    }

    @Test void explicitSmtpOptInWithoutSinkFailsBeforeSetup() {
        setupCalls.set(0);
        ProbeResult result = execute(SmtpProbe.class, Map.of("tc.tests.smtp", "true"));
        assertEquals(0, setupCalls.get());
        assertEquals(1, result.failed);
    }

    @Tag("db") static class DbProbe {
        @BeforeAll static void setup() { setupCalls.incrementAndGet(); }
        @Test void harmlessStandInForDatabaseWork() { }
    }
    @Tag("smtp") static class SmtpProbe {
        @BeforeAll static void setup() { setupCalls.incrementAndGet(); }
        @Test void harmlessStandInForMailWork() { }
    }
    static class MixedProbe {
        @Test void pure() { pureCalls.incrementAndGet(); }
        @Tag("db") @Test void external() { setupCalls.incrementAndGet(); }
    }

    private ProbeResult execute(Class<?> probe, Map<String, String> supplied) {
        Map<String, String> values = new HashMap<>(supplied);
        values.put("junit.jupiter.extensions.autodetection.enabled", "true");
        ConfigurationParameters parameters = new ConfigurationParameters() {
            public Optional<String> get(String key) { return Optional.ofNullable(values.get(key)); }
            public Optional<Boolean> getBoolean(String key) { return get(key).map(Boolean::parseBoolean); }
            public int size() { return values.size(); }
            public Set<String> keySet() { return values.keySet(); }
        };
        EngineDiscoveryRequest request = new EngineDiscoveryRequest() {
            public <T extends DiscoverySelector> List<T> getSelectorsByType(Class<T> type) {
                ClassSelector selector = DiscoverySelectors.selectClass(probe);
                return type.isInstance(selector) ? List.of(type.cast(selector)) : List.of();
            }
            public <T extends DiscoveryFilter<?>> List<T> getFiltersByType(Class<T> type) { return List.of(); }
            public ConfigurationParameters getConfigurationParameters() { return parameters; }
            public org.junit.platform.engine.reporting.OutputDirectoryProvider getOutputDirectoryProvider() {
                return new org.junit.platform.engine.reporting.OutputDirectoryProvider() {
                    public java.nio.file.Path getRootDirectory() { return java.nio.file.Path.of("target", "isolation-probes"); }
                    public java.nio.file.Path createOutputDirectory(TestDescriptor descriptor) {
                        return getRootDirectory();
                    }
                };
            }
        };
        ProbeResult result = new ProbeResult();
        JupiterTestEngine engine = new JupiterTestEngine();
        TestDescriptor root = engine.discover(request, UniqueId.forEngine("junit-jupiter"));
        assertFalse(root.getChildren().isEmpty(), "The harmless probe must actually be discovered");
        engine.execute(new ExecutionRequest(root, result, parameters));
        return result;
    }

    private static class ProbeResult implements EngineExecutionListener {
        int skipped;
        int failed;
        public void executionSkipped(TestDescriptor descriptor, String reason) { skipped++; }
        public void executionFinished(TestDescriptor descriptor, TestExecutionResult result) {
            if (result.getStatus() == TestExecutionResult.Status.FAILED) failed++;
        }
    }
}
