package vn.ticketscenter.fulfillment.checkin.controller;
import org.junit.jupiter.api.Test;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.identity.service.*;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.fulfillment.checkin.dto.*;
import vn.ticketscenter.fulfillment.checkin.cache.CheckInScanCache;
import vn.ticketscenter.fulfillment.checkin.security.SignedQrDecoder;
import vn.ticketscenter.fulfillment.checkin.repository.TicketCheckInRepository;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import java.lang.reflect.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class TicketCheckInServletTest {
    private final UUID actor=UUID.randomUUID(),event=UUID.randomUUID();private final Map<String,Object> attrs=new HashMap<>(),sessionAttrs=new HashMap<>(),contextAttrs=new HashMap<>();
    private final Map<String,String> headers=new HashMap<>();private String body="",type="application/json",method="POST",path="/api/check-ins";private int status=200,calls;private boolean sessionValid=true;private String forwarded;
    private final StringWriter output=new StringWriter();
    private final ServletContext context=proxy(ServletContext.class,(m,a)->switch(m){case "getAttribute"->contextAttrs.get(a[0]);case "getNamedDispatcher"->dispatcher((String)a[0]);default->null;});
    private final HttpSession session=proxy(HttpSession.class,(m,a)->m.equals("getAttribute")?sessionAttrs.get(a[0]):null);
    private final TicketCheckInService service=new TicketCheckInService(new TicketCheckInRepository(null),new SignedQrDecoder(new byte[32]),new CheckInScanCache(){public Lease lock(UUID id){return ()->{};}public void completed(UUID a,UUID k,CheckInScanResponseDto r){}}){
        @Override public CheckInScanResponseDto scan(UUID a,UUID k,CheckInScanRequestDto r){calls++;assertEquals(actor,a);return new CheckInScanResponseDto("CHECKED_IN_SUCCESS",UUID.randomUUID(),"CODE","<script>Khách</script>","Khu A",null,Instant.EPOCH,Instant.EPOCH,event);}
        @Override public List<RecentCheckInLogDto> recent(UUID a,UUID e,int limit){calls++;return List.of();}
    };
    TicketCheckInServletTest(){
        contextAttrs.put(AuthSecurityListener.AUTH_SERVICE_ATTRIBUTE,new AuthService(new TransactionManager(Map.of()),new UserRepository(),new PasswordHasher()){@Override public boolean isSessionValid(UUID a,int v){return sessionValid;}});
        attrs.put(SecurityContext.REQUEST_USER_ATTRIBUTE,new UserPrincipal(actor,PlatformRole.CUSTOMER,0));
        sessionAttrs.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE,"fixture-token");headers.put("X-CSRF-Token","fixture-token");headers.put("Idempotency-Key",UUID.randomUUID().toString());body="{\"eventId\":\""+event+"\",\"ticketCode\":\"CODE\"}";
    }
    private HttpServletRequest request(){return proxy(HttpServletRequest.class,(m,a)->switch(m){case "getAttribute"->attrs.get(a[0]);case "setAttribute"->{attrs.put((String)a[0],a[1]);yield null;}case "getSession"->session;case "getServletContext"->context;case "getHeader"->headers.get(a[0]);case "getContentType"->type;case "getReader"->new BufferedReader(new StringReader(body));case "getContentLengthLong"->(long)body.length();case "getRequestURI"->"/ticketscenter"+path;case "getContextPath"->"/ticketscenter";case "getMethod"->method;default->null;});}
    private HttpServletResponse response(){return proxy(HttpServletResponse.class,(m,a)->switch(m){case "getWriter"->new PrintWriter(output);case "setStatus"->{status=(int)a[0];yield null;}default->null;});}
    private RequestDispatcher dispatcher(String target){return proxy(RequestDispatcher.class,(m,a)->{if(m.equals("forward"))forwarded=target;return null;});}
    @Test void validMutationUsesSafeJsonResponse()throws Exception{new TicketCheckInServlet(service).doPost(request(),response());assertEquals(200,status);assertTrue(output.toString().contains("CHECKED_IN_SUCCESS"));assertEquals(1,calls);}
    @Test void anonymousSessionCannotReachService()throws Exception{attrs.clear();new TicketCheckInServlet(service).doPost(request(),response());assertEquals(401,status);assertEquals(0,calls);}
    @Test void expiredSessionCannotReachService()throws Exception{sessionValid=false;new TicketCheckInServlet(service).doPost(request(),response());assertEquals(401,status);assertEquals(0,calls);}
    @Test void csrfCannotBeBypassedByDirectServletDispatch()throws Exception{headers.remove("X-CSRF-Token");new TicketCheckInServlet(service).doPost(request(),response());assertEquals(403,status);assertEquals(0,calls);}
    @Test void invalidIdempotencyAndUnknownFieldsAreRejected()throws Exception{headers.put("Idempotency-Key","1");new TicketCheckInServlet(service).doPost(request(),response());assertEquals(400,status);assertEquals(0,calls);}
    @Test void oversizedOrNonJsonRequestsAreRejected()throws Exception{body="x".repeat(9000);new TicketCheckInServlet(service).doPost(request(),response());assertEquals(413,status);assertEquals(0,calls);}
    @Test void wrongContentTypeRejected()throws Exception{type="text/plain";new TicketCheckInServlet(service).doPost(request(),response());assertEquals(415,status);}
    @Test void malformedDuplicateFieldsRejected()throws Exception{body="{\"eventId\":\""+event+"\",\"ticketCode\":\"CODE\",\"ticketCode\":\"OTHER\"}";new TicketCheckInServlet(service).doPost(request(),response());assertEquals(400,status);assertEquals(0,calls);}
    @Test void getLogsRouteForwardsToFeatureServletAndPreservesEvent()throws Exception{method="GET";path="/api/events/"+event+"/check-ins";new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->fail("Wrong routing"));assertEquals("EventCheckInLogsServlet",forwarded);assertEquals(event.toString(),attrs.get(EventCheckInLogsServlet.EVENT_ATTRIBUTE));}
    @Test void nonGetLogsRouteReturns405()throws Exception{path="/api/events/"+event+"/check-ins";new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->fail("Wrong routing"));assertEquals(405,status);}
    @Test void scannerAliasUsesNamedForwardWithoutCollidingWithOrganizer()throws Exception{method="GET";path="/organizer/checkin-scanner";new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->fail("Wrong routing"));assertEquals("CheckInScannerServlet",forwarded);}
    @Test void unrelatedEventRoutePassesThrough()throws Exception{method="GET";path="/api/events/"+event;new CheckInEventRoutesFilter().doFilter(request(),response(),(a,b)->calls++);assertEquals(1,calls);assertNull(forwarded);}
    private interface Call{Object run(String m,Object[] a)throws Throwable;}
    @SuppressWarnings("unchecked")private static <T>T proxy(Class<T> type,Call call){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},(s,m,a)->{Object value=call.run(m.getName(),a==null?new Object[0]:a);if(value==null&&m.getReturnType()==boolean.class)return false;if(value==null&&m.getReturnType()==int.class)return 0;if(value==null&&m.getReturnType()==long.class)return 0L;return value;});}
}
