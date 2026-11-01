package vn.ticketscenter.report.dto;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.settlement.service.FinanceException;
import static org.junit.jupiter.api.Assertions.*;
class ReportExportScopeTest {
    Map<String,String> values(){return new HashMap<>(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z"));}
    @Test void organizationExportRequiresSelectedOrganization(){var p=values();p.put("scope","ORGANIZATION");assertEquals(400,assertThrows(FinanceException.class,()->ReportFilter.parse(p,null)).status());}
    @Test void platformExportCannotSilentlySwitchToOrganization(){var p=values();p.put("scope","PLATFORM");p.put("organizationId",UUID.randomUUID().toString());assertEquals(400,assertThrows(FinanceException.class,()->ReportFilter.parse(p,null)).status());}
    @Test void unknownScopeAndUnsupportedFormatAreRejected(){for(var entry:Map.of("scope","OTHER","format","xlsx").entrySet()){var p=values();p.put(entry.getKey(),entry.getValue());assertEquals(400,assertThrows(FinanceException.class,()->ReportFilter.parse(p,null)).status());}}
    @Test void explicitOrganizationAndCsvRetainCanonicalFilter(){var p=values();var org=UUID.randomUUID();p.put("scope","ORGANIZATION");p.put("format","csv");p.put("organizationId",org.toString());assertEquals(org,ReportFilter.parse(p,null).organizationId());}
}
