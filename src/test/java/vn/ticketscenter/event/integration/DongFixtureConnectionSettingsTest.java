package vn.ticketscenter.event.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DongFixtureConnectionSettingsTest {
    private static final String BASE="jdbc:sqlserver://127.0.0.1:1433;databaseName=TicketCenter_Dong_Vuong_Ci;";
    private static final String TAIL="encrypt=true;trustServerCertificate=true;loginTimeout=5";
    @Test void preservesExplicitWindowsAuthentication() {
        var settings=DongFixtureConnectionSettings.parse(BASE+"integratedSecurity=true;"+TAIL,null,null);
        assertTrue(settings.integrated());assertEquals("TicketCenter_Dong_Vuong_Ci",settings.database());
    }
    @Test void sqlCredentialsAreSeparateFromTheUrlAndRedacted() {
        var settings=DongFixtureConnectionSettings.parse(BASE+TAIL,"fixture_owner","private-test-value");
        assertFalse(settings.integrated());assertFalse(settings.toString().contains("private-test-value"));
    }
    @Test void missingOrMixedCredentialsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->DongFixtureConnectionSettings.parse(BASE+TAIL,null,null));
        assertThrows(IllegalArgumentException.class,()->DongFixtureConnectionSettings.parse(BASE+TAIL,"owner",""));
        assertThrows(IllegalArgumentException.class,()->DongFixtureConnectionSettings.parse(BASE+"integratedSecurity=true;"+TAIL,"owner","secret"));
    }
    @Test void remoteSharedAndOverriddenTargetsAreRejected() {
        for(String url:new String[]{(BASE+TAIL).replace("127.0.0.1","sql.example.com"),(BASE+TAIL).replace("TicketCenter_Dong_Vuong_Ci","TicketCenterticke"),BASE+TAIL+";databaseName=shared",BASE+TAIL+";failoverPartner=remote",BASE+TAIL+";user=owner;password=secret"})
            assertThrows(IllegalArgumentException.class,()->DongFixtureConnectionSettings.parse(url,"owner","secret"));
    }
    @Test void onlyValidTcpPortsAndBoundedTimeoutAreAllowed() {
        for(String url:new String[]{(BASE+TAIL).replace(":1433;",":0;"),(BASE+TAIL).replace(":1433;",":65536;"),(BASE+TAIL).replace("loginTimeout=5","loginTimeout=0")})
            assertThrows(IllegalArgumentException.class,()->DongFixtureConnectionSettings.parse(url,"owner","secret"));
    }
}
