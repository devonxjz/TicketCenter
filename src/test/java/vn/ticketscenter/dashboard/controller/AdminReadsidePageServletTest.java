package vn.ticketscenter.dashboard.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.settlement.dao.FinanceDatabase;
import static org.junit.jupiter.api.Assertions.*;

class AdminReadsidePageServletTest {
    @Test void currentAdminForwardsOnlyKnownAdminPages() throws Exception {
        for (String page : List.of("audit", "overview")) {
            var browser = new Browser("ADMIN", 0);
            browser.get("/finance/" + page);
            assertEquals("/WEB-INF/views/" + (page.equals("audit") ? "audit/audit.jsp" : "dashboard/overview.jsp"), browser.view);
            assertEquals(true, browser.attributes.get("financeAdmin"));
            assertEquals("", browser.attributes.get("financeOrganizationId"));
            assertEquals("no-store", browser.headers.get("Cache-Control"));
        }
    }
    @Test void managerAndStaffCannotForwardAdminOverviewOrAudit() throws Exception {
        for (String page : List.of("audit", "overview")) {
            var browser = new Browser("CUSTOMER", 0);
            browser.get("/finance/" + page);
            assertEquals(403, browser.status);
            assertNull(browser.view);
        }
    }
    @Test void staleAdminSessionCannotForwardEitherPage() throws Exception {
        for (String page : List.of("audit", "overview")) {
            var browser = new Browser("ADMIN", 1);
            browser.get("/finance/" + page);
            assertEquals(401, browser.status);
            assertNull(browser.view);
        }
    }
    @Test void unknownPageCannotForward() throws Exception {
        var browser = new Browser("ADMIN", 0);
        browser.get("/finance/other");
        assertEquals(404, browser.status);
        assertNull(browser.view);
    }
    static final class Browser {
        final String role; final int version;
        final Map<String,Object> attributes = new HashMap<>();
        final Map<String,String> headers = new HashMap<>();
        final StringWriter output = new StringWriter();
        int status; String view;
        Browser(String role, int version) { this.role=role; this.version=version; }
        void get(String path) throws Exception {
            attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, new UserPrincipal(UUID.randomUUID(), PlatformRole.ADMIN, 0));
            FinanceDatabase factory = () -> proxy(Connection.class, (method,args) -> {
                if (!method.equals("prepareStatement")) return null;
                assertTrue(((String)args[0]).contains("vw_ActorAuthority"), "Page must check current DB authority");
                boolean[] first={true};
                return proxy(PreparedStatement.class, (operation,values) -> {
                    if (!operation.equals("executeQuery")) return null;
                    return proxy(ResultSet.class, (name,columns) -> switch(name) {
                        case "next" -> {boolean result=first[0];first[0]=false;yield result;}
                        case "getString" -> "status".equals(columns[0]) ? "ACTIVE" : role;
                        case "getInt" -> version;
                        default -> null;
                    });
                });
            });
            ServletContext context=proxy(ServletContext.class,(name,args)->name.equals("getAttribute")?factory:null);
            ServletConfig config=proxy(ServletConfig.class,(name,args)->name.equals("getServletContext")?context:null);
            var servlet=new AdminReadsidePageServlet();servlet.init(config);
            HttpServletRequest request=proxy(HttpServletRequest.class,(name,args)->switch(name) {
                case "getAttribute" -> attributes.get(args[0]);
                case "setAttribute" -> {attributes.put((String)args[0],args[1]);yield null;}
                case "getServletPath" -> path;
                case "getRequestDispatcher" -> {String target=(String)args[0];yield proxy(RequestDispatcher.class,(method,values)->{if(method.equals("forward"))view=target;return null;});}
                default -> null;
            });
            HttpServletResponse response=proxy(HttpServletResponse.class,(name,args)->switch(name) {
                case "setStatus" -> {status=(int)args[0];yield null;}
                case "setHeader" -> {headers.put((String)args[0],(String)args[1]);yield null;}
                case "getWriter" -> new PrintWriter(output);
                case "isCommitted" -> false;
                default -> null;
            });
            servlet.doGet(request,response);
        }
    }
    interface Call {Object invoke(String name,Object[] args) throws Throwable;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Call call) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.invoke(m.getName(),a));
    }
}
