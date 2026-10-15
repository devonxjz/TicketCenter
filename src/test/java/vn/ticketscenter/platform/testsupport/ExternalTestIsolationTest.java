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

    @Test void legacyTargetsRegisterGuardEvenWhenDiscoveryIsDisabled() throws Exception {
        for(String name:List.of("vn.ticketscenter.MigrationSuiteVerificationTest",
                "vn.ticketscenter.config.seed.DemoAccountSeederTest",
                "vn.ticketscenter.identity.repository.OtpRepositoryTest",
                "vn.ticketscenter.identity.repository.OrganizationRepositoryTest",
                "vn.ticketscenter.identity.repository.OrganizationMembershipRepositoryTest",
                "vn.ticketscenter.identity.service.OtpServiceTest",
                "vn.ticketscenter.identity.service.PasswordResetServiceTest",
                "vn.ticketscenter.identity.service.AuthFlowTest",
                "vn.ticketscenter.config.persistence.JpaConnectionTest",
                "vn.ticketscenter.identity.integration.mail.EmailSenderTest")) {
            var type=Class.forName(name,false,getClass().getClassLoader());
            var annotation=type.getAnnotation(org.junit.jupiter.api.extension.ExtendWith.class);
            assertNotNull(annotation,"Explicit external guard required for "+name);
            assertTrue(Arrays.asList(annotation.value()).contains(ExternalTestCondition.class),name);
        }
    }

    @Test void mergedOwnerMigrationSuiteIsDisabledBeforeOpeningItsDatabase() {
        ProbeResult result=execute(vn.ticketscenter.MigrationSuiteVerificationTest.class,Map.of());
        assertEquals(1,result.skipped,"Owner migration verification must obey the default DB gate");
        assertEquals(0,result.failed,"Default tests must never open or alter the owner database");
    }
    @Test void directMigrationGuardSurvivesDisabledAutomaticDiscovery() {
        ProbeResult result=execute(vn.ticketscenter.MigrationSuiteVerificationTest.class,
                Map.of("junit.jupiter.extensions.autodetection.enabled","false"));
        assertEquals(1,result.skipped,"Explicit guard must stop migration before a connection is opened");
        assertEquals(0,result.failed);
    }
    @Test void directMembershipGuardSurvivesDisabledAutomaticDiscovery() {
        ProbeResult result=execute(vn.ticketscenter.identity.repository.OrganizationMembershipRepositoryTest.class,
                Map.of("junit.jupiter.extensions.autodetection.enabled","false"));
        assertEquals(1,result.skipped,"Real membership writes require an isolated DB opt-in");
        assertEquals(0,result.failed);
    }

    @Test void defaultDbGateStopsBeforeAll() {
        setupCalls.set(0);
        ProbeResult result = execute(DbProbe.class, Map.of());
        assertEquals(0, setupCalls.get(), "DB setup must be disabled before @BeforeAll");
        assertEquals(1, result.skipped);
    }

    @Test void organizationRepositoryIntegrationIsDisabledBeforeDatabaseAccess() {
        ProbeResult result = execute(vn.ticketscenter.identity.repository.OrganizationRepositoryTest.class, Map.of());
        assertEquals(1, result.skipped, "Organization fixture creation must require explicit isolated DB opt-in");
        assertEquals(0, result.failed);
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
        values.putIfAbsent("junit.jupiter.extensions.autodetection.enabled", "true");
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
