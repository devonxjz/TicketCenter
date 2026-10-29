package vn.ticketscenter.report.controller;
import org.junit.jupiter.api.Test;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.*;
import vn.ticketscenter.settlement.service.FinanceException;
import static org.junit.jupiter.api.Assertions.*;
class ReadsideWebTest {
    @Test void ambiguousDuplicateParametersAreRejected(){HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->m.getName().equals("getParameterMap")?Map.of("organizationId",new String[]{"A","B"}):null);assertEquals(400,assertThrows(FinanceException.class,()->ReadsideWeb.parameters(req)).status());}
    @Test void organizationRouteRequiresCanonicalUuidAndKnownOperation(){UUID org=UUID.randomUUID();assertEquals(org,ReadsideWeb.organizationRoute("/"+org+"/reports")[0]);assertThrows(FinanceException.class,()->ReadsideWeb.organizationRoute("/1/reports"));assertThrows(FinanceException.class,()->ReadsideWeb.organizationRoute("/"+org+"/members"));}
    @Test void overviewHasAnExactOrganizationRoute(){UUID org=UUID.randomUUID();var route=assertDoesNotThrow(()->ReadsideWeb.organizationRoute("/"+org+"/overview"));assertEquals(org,route[0]);assertEquals("overview",route[1]);for(String suffix:List.of("overview/","overview/extra","members"))assertEquals(404,assertThrows(FinanceException.class,()->ReadsideWeb.organizationRoute("/"+org+"/"+suffix)).status());}
}
