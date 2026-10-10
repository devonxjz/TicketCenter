package vn.ticketscenter.platform.testsupport;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ExternalTestTargetsTest {
    private Map<String, String> db() {
        return new HashMap<>(Map.of("tc.tests.db.name", "TC_Vuong_Test_probe", "db.name", "TC_Vuong_Test_probe",
                "db.url", "jdbc:sqlserver://127.0.0.1:1433;databaseName=TC_Vuong_Test_probe;loginTimeout=5;encrypt=false"));
    }
    @Test void acceptsOnlyExplicitDedicatedLoopbackDatabase() {
        var values = db();
        assertDoesNotThrow(() -> ExternalTestTargets.requireDatabase(key -> values.getOrDefault(key, "")));
    }
    @Test void rejectsSharedDatabaseAndMissingTimeout() {
        var values = db();
        values.put("db.url", "jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketsCenter_Dev;loginTimeout=5");
        assertThrows(IllegalStateException.class, () -> ExternalTestTargets.requireDatabase(key -> values.getOrDefault(key, "")));
        values.put("db.url", "jdbc:sqlserver://127.0.0.1:1433;databaseName=TC_Vuong_Test_probe");
        assertThrows(IllegalStateException.class, () -> ExternalTestTargets.requireDatabase(key -> values.getOrDefault(key, "")));
    }
    @Test void rejectsRemoteFailoverAndDuplicateJdbcOptions() {
        for (String url : new String[]{
                "jdbc:sqlserver://db.example.test;databaseName=TC_Vuong_Test_probe;loginTimeout=5",
                "jdbc:sqlserver://127.0.0.1;databaseName=TC_Vuong_Test_probe;loginTimeout=5;failoverPartner=db.example.test",
                "jdbc:sqlserver://127.0.0.1;databaseName=TC_Vuong_Test_probe;loginTimeout=5;databaseName=Shared"}) {
            var values = db(); values.put("db.url", url);
            assertThrows(IllegalStateException.class, () -> ExternalTestTargets.requireDatabase(key -> values.getOrDefault(key, "")));
        }
    }
    private Map<String, String> smtp() {
        return new HashMap<>(Map.of("tc.tests.smtp.sink", "loopback-tls-no-relay",
                "mail.smtp.host", "127.0.0.1", "mail.smtp.port", "1465",
                "mail.smtp.user", "sink-probe", "mail.smtp.password", "nonsecret-sink-probe"));
    }
    @Test void controlledSinkUsesActualMailConfigPropertyNames() {
        var values = smtp();
        assertDoesNotThrow(() -> ExternalTestTargets.requireSmtpSink(key -> values.getOrDefault(key, "")));
    }
    @Test void rejectsRemoteMailHostAndMissingCredentials() {
        var values = smtp(); values.put("mail.smtp.host", "smtp.example.test");
        assertThrows(IllegalStateException.class, () -> ExternalTestTargets.requireSmtpSink(key -> values.getOrDefault(key, "")));
        values.put("mail.smtp.host", "127.0.0.1"); values.remove("mail.smtp.password");
        assertThrows(IllegalStateException.class, () -> ExternalTestTargets.requireSmtpSink(key -> values.getOrDefault(key, "")));
    }
}
