package vn.ticketscenter.report.controller;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import vn.ticketscenter.settlement.infrastructure.FinanceException;
import static org.junit.jupiter.api.Assertions.*;
class ReadsideHttpTest {
    @Test void ambiguousDuplicateParametersAreRejected(){HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->m.getName().equals("getParameterMap")?Map.of("organizationId",new String[]{"A","B"}):null);assertEquals(400,assertThrows(FinanceException.class,()->ReadsideHttp.parameters(req)).status());}
    @Test void organizationRouteRequiresCanonicalUuidAndKnownOperation(){UUID org=UUID.randomUUID();assertEquals(org,ReadsideHttp.organizationRoute("/"+org+"/reports")[0]);assertThrows(FinanceException.class,()->ReadsideHttp.organizationRoute("/1/reports"));assertThrows(FinanceException.class,()->ReadsideHttp.organizationRoute("/"+org+"/members"));}
}
