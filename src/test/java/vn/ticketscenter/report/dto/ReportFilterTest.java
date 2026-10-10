package vn.ticketscenter.report.dto;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReportFilterTest {
    private Map<String,String> values() { return new HashMap<>(Map.of("metric","COHORT_REVENUE","from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z")); }
    @Test void requiresExplicitUtcHalfOpenRange() {
        var values=values(); var f=ReportFilter.parse(values,null);
        assertEquals("2026-10-01T00:00:00Z",f.from().toString());
        values.put("to",values.get("from")); assertThrows(FinanceException.class,()->ReportFilter.parse(values,null));
        values.put("to","2026-10-02"); assertThrows(FinanceException.class,()->ReportFilter.parse(values,null));
    }
    @Test void routeScopeOverridesOnlyMatchingOrganization() {
        var org=UUID.randomUUID(); var values=values(); assertEquals(org,ReportFilter.parse(values,org).organizationId());
        values.put("organizationId",UUID.randomUUID().toString()); assertEquals(400,assertThrows(FinanceException.class,()->ReportFilter.parse(values,org)).status());
    }
    @Test void metricAndPagingAreBoundedWithoutIntegerOverflow() {
        var values=values(); values.put("metric","SUM(total)"); assertThrows(FinanceException.class,()->ReportFilter.parse(values,null));
        values.put("metric","CASH_FLOW"); values.put("pageSize","101"); assertThrows(FinanceException.class,()->ReportFilter.parse(values,null));
        values.put("pageSize","100"); values.put("page","2147483647"); assertThrows(FinanceException.class,()->ReportFilter.parse(values,null));
    }
    @Test void cacheIdentityIncludesEveryFilterAndExcludesPageOnlyForExport() {
        var values=values(); var f=ReportFilter.parse(values,UUID.randomUUID()); values.put("eventId",UUID.randomUUID().toString());
        assertNotEquals(f.cacheIdentity(),ReportFilter.parse(values,f.organizationId()).cacheIdentity());
    }
}
