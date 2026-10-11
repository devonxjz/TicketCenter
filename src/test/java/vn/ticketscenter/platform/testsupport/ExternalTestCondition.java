package vn.ticketscenter.platform.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.sql.DriverManager;
import java.util.Set;
import java.util.function.Function;

/** Discovered only on the test classpath; never included in the application WAR. */
public final class ExternalTestCondition implements ExecutionCondition {
    private static final String IDENTITY = "vn.ticketscenter.identity.";
    private static final Set<String> DB_CLASSES = Set.of(
            IDENTITY + "repository.OtpRepositoryTest",
            IDENTITY + "service.OtpServiceTest",
            IDENTITY + "service.PasswordResetServiceTest",
            "vn.ticketscenter.config.seed.DemoAccountSeederTest");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        String className = context.getTestClass().map(Class::getName).orElse("");
        String method = context.getTestMethod().map(java.lang.reflect.Method::getName).orElse("");
        boolean db = context.getTags().contains("db") || DB_CLASSES.contains(className)
                || className.endsWith("IT")
                || (className.equals(IDENTITY + "repository.OrganizationRepositoryTest") && method.equals("testRepositoryIntegration"))
                || (className.equals(IDENTITY + "service.AuthServiceTest") && method.equals("testFullAuthFlowWithDatabase"))
                || (className.equals("vn.ticketscenter.config.persistence.JpaConnectionTest")
                    && Set.of("testDatabaseConnectivity", "testPersistenceRegistry").contains(method));
        boolean smtp = context.getTags().contains("smtp")
                || className.equals(IDENTITY + "integration.PasswordResetIT")
                || (className.equals(IDENTITY + "integration.mail.EmailSenderTest")
                    && method.equals("smtpEmailSenderShouldSendRealEmail"));
        if (!db && !smtp) return ConditionEvaluationResult.enabled("Pure test: no external setup");
        Function<String, String> config = key -> ExternalTestConfiguration.resolve(key,
                parameter -> context.getConfigurationParameter(parameter).orElse(""), System::getenv);
        if (db && !"true".equals(config.apply("tc.tests.db"))) {
            return ConditionEvaluationResult.disabled("DB IO disabled: explicit tc.tests.db=true and isolated fixture required");
        }
        if (smtp && !"true".equals(config.apply("tc.tests.smtp"))) {
            return ConditionEvaluationResult.disabled("SMTP IO disabled: explicit tc.tests.smtp=true and controlled sink required");
        }
        if (db) ExternalTestTargets.requireDatabase(config);
        if (smtp) ExternalTestTargets.requireSmtpSink(config);
        if (db) {
            ExtensionContext.Store store = context.getRoot().getStore(ExtensionContext.Namespace.create(ExternalTestCondition.class));
            String target = config.apply("db.url");
            store.getOrComputeIfAbsent(target, ignored -> verifyFixture(config), Boolean.class);
        }
        return ConditionEvaluationResult.enabled("Explicit external-test opt-in validated before setup");
    }

    private boolean verifyFixture(Function<String, String> config) {
        // No classpath credentials or fallback target: use only explicitly supplied test parameters.
        try (var connection = DriverManager.getConnection(config.apply("db.url"),
                config.apply("db.user"), config.apply("db.password"));
             var statement = connection.prepareStatement(
                     "SELECT fixture_key FROM dbo.tc_vuong_test_fixture WHERE fixture_key = ?")) {
            statement.setQueryTimeout(5);
            statement.setString(1, "vuong-finance-v1");
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new IllegalStateException("Dedicated DB fixture marker is missing");
            }
            return true;
        } catch (Exception ignored) {
            // JDBC exceptions may contain URLs/account information. Never expose their text/cause.
            throw new IllegalStateException("Dedicated DB fixture preflight failed; no test setup executed");
        }
    }
}
