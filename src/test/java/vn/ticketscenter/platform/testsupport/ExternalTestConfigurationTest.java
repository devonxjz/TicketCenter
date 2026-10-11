package vn.ticketscenter.platform.testsupport;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExternalTestConfigurationTest {
    @Test void environmentProvidesExplicitRuntimeDatabaseConfiguration() {
        var env = Map.of("DB_URL", "jdbc:sqlserver://127.0.0.1:1433;databaseName=TC_Vuong_Test_probe;loginTimeout=5",
                "DB_NAME", "TC_Vuong_Test_probe", "DB_USER", "probe-user", "DB_PASSWORD", "nonsecret-probe");
        assertEquals(env.get("DB_PASSWORD"), ExternalTestConfiguration.resolve("db.password", key -> "", env::get));
        assertEquals(env.get("DB_URL"), ExternalTestConfiguration.resolve("db.url", key -> "", env::get));
    }
    @Test void optInFlagsCannotBeActivatedByEnvironmentFallback() {
        assertEquals("", ExternalTestConfiguration.resolve("tc.tests.db", key -> "", key -> "true"));
    }
    @Test void explicitJvmTargetWinsOverEnvironment() {
        assertEquals("TC_Vuong_Test_selected", ExternalTestConfiguration.resolve("db.name",
                key -> "TC_Vuong_Test_selected", key -> "TC_Vuong_Test_other"));
    }
}
