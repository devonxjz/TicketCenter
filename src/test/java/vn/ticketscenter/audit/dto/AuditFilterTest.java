package vn.ticketscenter.audit.dto;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AuditFilterTest {
    Map<String,String> values(){return new HashMap<>(Map.of("from","2026-10-01T00:00:00Z","to","2026-10-02T00:00:00Z"));}
    @Test void rejectsInvalidActionAndDateRange(){var v=values();v.put("action","' OR 1=1");assertThrows(FinanceException.class,()->AuditFilter.parse(v));v.remove("action");v.put("to",v.get("from"));assertThrows(FinanceException.class,()->AuditFilter.parse(v));}
    @Test void normalizesValidExplicitFilters(){var v=values();v.put("action","EVENT_STATUS_CHANGED");v.put("entityType","Event");v.put("entityId",UUID.randomUUID().toString());var f=AuditFilter.parse(v);assertEquals(20,f.pageSize());assertEquals("Event",f.entityType());}
}
