package vn.ticketscenter.report.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.settlement.infrastructure.FinanceDatabase;
import static org.junit.jupiter.api.Assertions.*;

class FinanceReadsidePageServletTest {
    @Test void managerPageSelectsOwnedOrganizationBeforeForward() throws Exception {
        var browser = new Browser(true, 0);
        browser.get("/finance/reports");
        assertEquals("/WEB-INF/views/report/reports.jsp", browser.view);
        assertEquals(browser.organization.toString(), browser.attributes.get("financeOrganizationId"));
        assertEquals(false, browser.attributes.get("financeAdmin"));
        assertTrue(browser.output.toString().isEmpty());
    }
    @Test void actorWithNoManagerMembershipGetsForbiddenInsteadOfMalformedFilter() throws Exception {
        var browser = new Browser(false, 0);
        browser.get("/finance/reports");
        assertEquals(403, browser.status);
        assertNull(browser.view);
        assertTrue(browser.output.toString().contains("FORBIDDEN"));
    }
    @Test void staleSessionIsDeniedBeforeForwardingAnyPage() throws Exception {
        var browser = new Browser(true, 1);
        browser.get("/finance/reports");
        assertEquals(401, browser.status);
        assertNull(browser.view);
        assertFalse(browser.attributes.containsKey("financeOrganizationId"));
    }
    static final class Browser {
        final UUID user = UUID.randomUUID(), organization = UUID.randomUUID();
        final boolean manager; final int version;
        final Map<String,Object> attributes = new HashMap<>();
        final StringWriter output = new StringWriter(); int status; String view;
        Browser(boolean manager,int version) {this.manager=manager;this.version=version;}
        void get(String path) throws Exception {
            attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE,new UserPrincipal(user,PlatformRole.CUSTOMER,0));
            FinanceDatabase factory = () -> proxy(Connection.class,(method,args)-> {
                if(!method.equals("prepareStatement"))return null;
                boolean actor=((String)args[0]).contains("vw_ActorAuthority"); boolean[] first={true};
                return proxy(PreparedStatement.class,(operation,values)-> {
                    if(!operation.equals("executeQuery"))return null;
                    return proxy(ResultSet.class,(name,columns)-> switch(name) {
                        case "next" -> {boolean result=first[0]&&(actor||manager);first[0]=false;yield result;}
                        case "getString" -> actor? ("status".equals(columns[0])?"ACTIVE":"CUSTOMER"):organization.toString();
                        case "getInt" -> version;
                        default -> null;
                    });
                });
            });
            ServletContext context=proxy(ServletContext.class,(name,args)->name.equals("getAttribute")?factory:null);
            ServletConfig config=proxy(ServletConfig.class,(name,args)->name.equals("getServletContext")?context:null);
            var servlet=new FinanceReadsidePageServlet();servlet.init(config);
            HttpServletRequest request=proxy(HttpServletRequest.class,(name,args)->switch(name) {
                case "getAttribute" -> attributes.get(args[0]);
                case "setAttribute" -> {attributes.put((String)args[0],args[1]);yield null;}
                case "getParameterMap" -> Map.of();
                case "getServletPath" -> path;
                case "getRequestDispatcher" -> {String destination=(String)args[0];yield proxy(RequestDispatcher.class,(method,values)->{if(method.equals("forward"))view=destination;return null;});}
                default -> null;
            });
            HttpServletResponse response=proxy(HttpServletResponse.class,(name,args)->switch(name) {
                case "setStatus" -> {status=(int)args[0];yield null;}
                case "getWriter" -> new PrintWriter(output);
                case "isCommitted" -> false;
                default -> null;
            });
            servlet.doGet(request,response);
        }
    }
    interface Call {Object invoke(String name,Object[] args) throws Throwable;}
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Call call) {
        return(T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.invoke(m.getName(),a));
    }
}
