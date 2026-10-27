package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInEventCache;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository;
import vn.ticketscenter.fulfillment.checkin.dao.CheckInEventRepository.EventInfo;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventServiceImpl;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.identity.service.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CheckInServletsTest {
    private static final UUID ACTOR=UUID.randomUUID(),EVENT=UUID.randomUUID(),ORG=UUID.randomUUID();
    private final Map<String,Object> attributes=new HashMap<>(),contextAttributes=new HashMap<>();
    private final Map<String,String> parameters=new HashMap<>(),headers=new HashMap<>(),responseHeaders=new HashMap<>();
    private final StringWriter output=new StringWriter();
    private int status=200;private String path="/api/check-ins/events",method="GET",forwarded;
    private boolean sessionValid=true;private int serviceCalls;
    private RuntimeException failure;
    private final ServletContext context=proxy(ServletContext.class,(m,a)->switch(m){
        case "getAttribute" -> contextAttributes.get(a[0]);
        case "setAttribute" -> {contextAttributes.put((String)a[0],a[1]);yield null;}
        case "getNamedDispatcher" -> dispatcher("named:"+a[0]);
        default -> null;
    });
    private final CheckInEventService service=new CheckInEventServiceImpl(new CheckInEventRepository(null),new EmptyCache(),Clock.systemUTC()) {
        @Override public List<CheckInEventSummaryDto> events(UUID actor,UUID org){
            serviceCalls++;if(failure!=null)throw failure;assertEquals(ACTOR,actor);
            return List.of(new CheckInEventSummaryDto(EVENT,"<script>\"Nhạc\"</script>","Nhà hát",Instant.EPOCH,Instant.EPOCH.plusSeconds(7200),Instant.EPOCH.minusSeconds(3600),true,5,2,"OPEN"));
        }
        @Override public CheckInWindowStatusDto window(UUID actor,UUID event){
            serviceCalls++;if(failure!=null)throw failure;assertEquals(ACTOR,actor);assertEquals(EVENT,event);
            return new CheckInWindowStatusDto(EVENT,true,"OPEN",Instant.EPOCH,Instant.EPOCH.plusSeconds(7200),Instant.EPOCH,"Đang mở.");
        }
    };
    CheckInServletsTest(){
        contextAttributes.put(IdentityServices.SESSION_VALIDATOR_ATTRIBUTE,new SessionValidator(new TransactionManager(Map.of()),new UserRepository()) {
            @Override public boolean isSessionValid(UUID actor,int version){return sessionValid;}
        });
    }
    private void login(){attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE,new UserPrincipal(ACTOR,PlatformRole.CUSTOMER,1));}
    private HttpServletRequest request(){return proxy(HttpServletRequest.class,(m,a)->switch(m){
        case "getAttribute" -> attributes.get(a[0]);
        case "setAttribute" -> {attributes.put((String)a[0],a[1]);yield null;}
        case "removeAttribute" -> {attributes.remove(a[0]);yield null;}
        case "getServletContext" -> context;
        case "getParameter" -> parameters.get(a[0]);
        case "getHeader" -> headers.get(a[0]);
        case "getServletPath" -> path;
        case "getRequestURI" -> "/ticketscenter"+path;
        case "getContextPath" -> "/ticketscenter";
        case "getMethod" -> method;
        case "getRequestDispatcher" -> dispatcher((String)a[0]);
        default -> null;
    });}
    private HttpServletResponse response(){return proxy(HttpServletResponse.class,(m,a)->switch(m){
        case "setStatus" -> {status=(int)a[0];yield null;}
        case "getWriter" -> new PrintWriter(output);
        case "setHeader" -> {responseHeaders.put((String)a[0],(String)a[1]);yield null;}
        case "sendRedirect" -> {status=302;responseHeaders.put("Location",(String)a[0]);yield null;}
        default -> null;
    });}
    private RequestDispatcher dispatcher(String target){return proxy(RequestDispatcher.class,(m,a)->{if(m.equals("forward"))forwarded=target;return null;});}

    @Test void unauthenticatedListReturns401WithoutServiceAccess() throws Exception {
        new CheckInEventsServlet(service).doGet(request(),response());assertEquals(401,status);assertEquals(0,serviceCalls);
    }
    @Test void jsonListUsesEnvelopeAndEscapesDynamicText() throws Exception {
        login();parameters.put("organizationId",ORG.toString());new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals(200,status);assertTrue(output.toString().startsWith("{\"data\":["));
        assertTrue(output.toString().contains("\\\"Nhạc\\\""));assertFalse(output.toString().contains("paidAmount"));
        assertEquals("no-store",responseHeaders.get("Cache-Control"));
    }
    @Test void malformedOrganizationIdIsRejectedBeforeService() throws Exception {
        login();parameters.put("organizationId","1 OR 1=1");new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals(400,status);assertEquals(0,serviceCalls);
    }
    @Test void staleSessionIsRejectedForHtmlAsWellAsApi() throws Exception {
        login();sessionValid=false;path="/check-ins";new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals(401,status);assertEquals(0,serviceCalls);assertFalse(attributes.containsKey(SecurityContext.REQUEST_USER_ATTRIBUTE));
    }
    @Test void htmlPageForwardsToOwnedJsp() throws Exception {
        login();path="/check-ins";new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals("/WEB-INF/views/checkin/select-event.jsp",forwarded);assertNotNull(attributes.get("checkInEvents"));
    }
    @Test void explicitJsonAcceptOnHtmlRouteReturnsSameData() throws Exception {
        login();path="/check-ins";headers.put("Accept","application/json");new CheckInEventsServlet(service).doGet(request(),response());
        assertNull(forwarded);assertTrue(output.toString().contains("totalCheckedIn"));
    }
    @Test void unavailableAuthenticationFailsClosed() throws Exception {
        login();contextAttributes.clear();new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals(503,status);assertEquals(0,serviceCalls);
    }
    @Test void databaseDetailsNeverLeakToClient() throws Exception {
        login();failure=new IllegalStateException("SQL password=secret dbo.tc_users");new CheckInEventsServlet(service).doGet(request(),response());
        assertEquals(503,status);assertFalse(output.toString().contains("secret"));assertFalse(output.toString().contains("tc_users"));
    }
    @Test void cancelledWindowReturnsRequired400() throws Exception {
        login();attributes.put(CheckInWindowServlet.EVENT_ID_ATTRIBUTE,EVENT.toString());failure=new CheckInException(400,"EVENT_CANCELLED","Sự kiện đã bị hủy bỏ.");
        new CheckInWindowServlet(service).doGet(request(),response());assertEquals(400,status);assertTrue(output.toString().contains("EVENT_CANCELLED"));
    }
    @Test void windowReturnsServerTimeAndOpenStatus() throws Exception {
        login();attributes.put(CheckInWindowServlet.EVENT_ID_ATTRIBUTE,EVENT.toString());new CheckInWindowServlet(service).doGet(request(),response());
        assertEquals(200,status);assertTrue(output.toString().contains("\"isOpen\":true"));assertTrue(output.toString().contains("currentServerTime"));
    }
    @Test void nestedRouteDispatchesOnlyCheckInWindow() throws Exception {
        path="/api/events/"+EVENT+"/check-in-window";boolean[] chained={false};
        new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->chained[0]=true);
        assertFalse(chained[0]);assertEquals("named:CheckInWindowServlet",forwarded);assertEquals(EVENT.toString(),attributes.get(CheckInWindowServlet.EVENT_ID_ATTRIBUTE));
    }
    @Test void unrelatedEventRoutesRemainWithTheirOwner() throws Exception {
        path="/api/events/"+EVENT+"/zones";boolean[] chained={false};
        new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->chained[0]=true);assertTrue(chained[0]);assertNull(forwarded);
    }
    @Test void windowRouteCannotMutateViaPost() throws Exception {
        path="/api/events/"+EVENT+"/check-in-window";method="POST";
        new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->fail("Unexpected forwarding"));
        assertEquals(405,status);assertEquals("GET",responseHeaders.get("Allow"));
    }
    @Test void organizerNavigationUsesRealUi15AndPreservesOrganizationFilter() throws Exception {
        path="/organizer/checkin-select";parameters.put("organizationId",ORG.toString());
        new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->fail("Static demo must not handle UI-15"));
        assertEquals(302,status);assertEquals("/ticketscenter/check-ins?organizationId="+ORG,responseHeaders.get("Location"));
    }

    private interface Call {Object run(String method,Object[] arguments) throws Throwable;}
    @SuppressWarnings("unchecked") private static <T>T proxy(Class<T> type,Call call){
        return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(self,method,args)->{
            if(method.getName().equals("toString"))return type.getSimpleName();
            if(method.getName().equals("hashCode"))return System.identityHashCode(self);
            if(method.getName().equals("equals"))return self==args[0];
            Object value=call.run(method.getName(),args==null?new Object[0]:args);
            if(value==null&&method.getReturnType()==boolean.class)return false;
            if(value==null&&method.getReturnType()==int.class)return 0;
            if(value==null&&method.getReturnType()==long.class)return 0L;
            return value;
        });
    }
    private static final class EmptyCache implements CheckInEventCache {
        public Optional<List<EventInfo>> events(UUID a,UUID o){return Optional.empty();}public void putEvents(UUID a,UUID o,List<EventInfo> e){}
        public Optional<EventInfo> window(UUID e){return Optional.empty();}public void putWindow(EventInfo e){}public void invalidate(UUID e,UUID o){}
    }
}
