package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import vn.ticketscenter.config.error.AppException;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.fulfillment.refund.service.*;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.service.*;
import vn.ticketscenter.identity.dao.UserRepository;
import static org.junit.jupiter.api.Assertions.*;

class RefundRequestServletsTest {
    final UUID order=UUID.randomUUID(),ticket=UUID.randomUUID(),key=UUID.randomUUID();
    final UserPrincipal actor=new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,0);
    final Map<String,Object> attributes=new HashMap<>(),contextAttributes=new HashMap<>(),sessionAttributes=new HashMap<>();
    final Map<String,String> headers=new HashMap<>(),responseHeaders=new HashMap<>();
    final StringWriter output=new StringWriter();String body,path="/api/refund-requests",forwarded,method="POST";int status=200,creates;boolean sessionValid=true;
    final ServletContext context=proxy(ServletContext.class,(name,args)->switch(name){case "getAttribute"->contextAttributes.get(args[0]);case "setAttribute"->{contextAttributes.put((String)args[0],args[1]);yield null;}case "getNamedDispatcher"->dispatcher("named:"+args[0]);default->null;});
    RefundRequestServletsTest(){
        body="{\"orderId\":\""+order+"\",\"ticketIds\":[\""+ticket+"\"],\"reason\":\"Không thể tham dự sự kiện.\"}";
        contextAttributes.put(IdentityServices.SESSION_VALIDATOR_ATTRIBUTE,new SessionValidator(new TransactionManager(Map.of()),new UserRepository()){@Override public boolean isSessionValid(UUID id,int version){return sessionValid;}});
        contextAttributes.put(RefundRuntime.SERVICE_ATTRIBUTE,new RefundRequestServiceImpl(new NoStore(),RefundRequestCache.NONE){
            @Override public RefundSummaryDto create(UserPrincipal a,CreateRefundRequestDto r,UUID k){creates++;assertEquals(actor,a);assertEquals(order,r.orderId());assertEquals(List.of(ticket),r.ticketIds());assertEquals(key,k);return new RefundSummaryDto(UUID.randomUUID(),order,"123456","REQUESTED",1,Instant.EPOCH,"Sự kiện","Không thể tham dự",null);}
        });
    }
    void login(){attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE,actor);sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE,"csrf-valid");headers.put("X-CSRF-Token","csrf-valid");headers.put("Idempotency-Key",key.toString());}
    HttpServletRequest request(){return proxy(HttpServletRequest.class,(n,a)->switch(n){case "getServletContext"->context;case "getAttribute"->attributes.get(a[0]);case "setAttribute"->{attributes.put((String)a[0],a[1]);yield null;}case "removeAttribute"->{attributes.remove(a[0]);yield null;}case "getSession"->proxy(HttpSession.class,(m,b)->m.equals("getAttribute")?sessionAttributes.get(b[0]):null);case "getHeader"->headers.get(a[0]);case "getContentType"->"application/json;charset=UTF-8";case "getReader"->new BufferedReader(new StringReader(body));case "getRequestURI"->"/tc"+path;case "getContextPath"->"/tc";case "getMethod"->method;case "getParameter"->null;case "getRequestDispatcher"->dispatcher((String)a[0]);default->null;});}
    HttpServletResponse response(){return proxy(HttpServletResponse.class,(n,a)->switch(n){case "setStatus"->{status=(int)a[0];yield null;}case "setHeader"->{responseHeaders.put((String)a[0],(String)a[1]);yield null;}case "getWriter"->new PrintWriter(output);default->null;});}
    RefundRequestServlet servlet()throws Exception{var s=new RefundRequestServlet();s.init(proxy(ServletConfig.class,(n,a)->n.equals("getServletContext")?context:null));return s;}
    RequestDispatcher dispatcher(String target){return proxy(RequestDispatcher.class,(n,a)->{if(n.equals("forward"))forwarded=target;return null;});}
    @Test void myRefundRoutesReturnDataAndForwardJsp()throws Exception{login();method="GET";path="/api/me/refund-requests";servlet().doGet(request(),response());assertEquals(200,status);assertTrue(output.toString().contains("\"data\":"));path="/my-refunds";servlet().doGet(request(),response());assertEquals("/WEB-INF/views/refund/my-refunds.jsp",forwarded);assertNotNull(attributes.get("refundPage"));}
    @Test void createReturns201EnvelopeAndIntegerVnd()throws Exception{login();servlet().doPost(request(),response());assertEquals(201,status);assertEquals(1,creates);assertTrue(output.toString().startsWith("{\"data\":{"));assertTrue(output.toString().contains("\"totalRefundAmount\":\"123456\""));assertTrue(output.toString().contains("\"message\":"));assertTrue(output.toString().endsWith("}}"));assertEquals("no-store",responseHeaders.get("Cache-Control"));}
    @Test void missingSessionReturns401WithoutMutation()throws Exception{servlet().doPost(request(),response());assertEquals(401,status);assertEquals(0,creates);}
    @Test void revokedSessionReturns401WithoutMutation()throws Exception{login();sessionValid=false;servlet().doPost(request(),response());assertEquals(401,status);assertEquals(0,creates);}
    @Test void badCsrfReturns403WithoutMutation()throws Exception{login();headers.remove("X-CSRF-Token");servlet().doPost(request(),response());assertEquals(403,status);assertEquals(0,creates);}
    @Test void invalidIdempotencyKeyReturns400()throws Exception{login();headers.put("Idempotency-Key","123");servlet().doPost(request(),response());assertEquals(400,status);assertEquals(0,creates);}
    @Test void clientAmountIsRejected()throws Exception{login();body=body.substring(0,body.length()-1)+",\"amount\":\"999\"}";servlet().doPost(request(),response());assertEquals(400,status);assertEquals(0,creates);}
    @Test void nestedDuplicateMalformedAndOversizeInputsAreRejected()throws Exception{login();String original=body;for(String bad:List.of(original.replace("[\""+ticket+"\"]","[[\""+ticket+"\"]]"),original.replace("[\""+ticket+"\"]","[\""+ticket+"\",\""+ticket+"\"]"),original+"garbage",original.replace("Không thể tham dự sự kiện.","x".repeat(33000)),original.replace("\"orderId\":", "\"reason\":\"duplicate\",\"orderId\":"))){body=bad;status=200;servlet().doPost(request(),response());assertEquals(400,status);}assertEquals(0,creates);}
    @Test void exactOrderRefundSubrouteForwardsIdAndLeavesOtherOrderRoutesAlone()throws Exception{method="GET";path="/api/orders/"+order+"/refundable-tickets";var filter=new RefundOrderRoutesFilter();boolean[] passed={false};filter.doFilter(request(),response(),(r,s)->passed[0]=true);assertEquals("named:OrderRefundableTicketsServlet",forwarded);assertEquals(order.toString(),attributes.get(OrderRefundableTicketsServlet.ORDER_ID_ATTRIBUTE));assertFalse(passed[0]);path="/api/orders/"+order+"/tickets";filter.doFilter(request(),response(),(r,s)->passed[0]=true);assertTrue(passed[0]);}
    @Test void refundOrderRouteRejectsWrites()throws Exception{path="/api/orders/"+order+"/refundable-tickets";new RefundOrderRoutesFilter().doFilter(request(),response(),(r,s)->fail());assertEquals(405,status);}
    @Test void sqlErrorTableFailuresKeepTheirStatusAndCode()throws Exception{
        RefundWeb.error(response(),new AppException(400,"REFUND_NOT_ELIGIBLE","Vé không đủ điều kiện hoàn trả."));
        assertEquals(400,status);
        assertTrue(output.toString().contains("\"code\":\"REFUND_NOT_ELIGIBLE\""));
    }
    @Test void canonicalUuidParserRejectsNumericAndShortIds(){for(String value:List.of("12","1-1-1-1-1",order+"/x"))assertThrows(RefundException.class,()->RefundWeb.id(value));}
    @Test void requestRejectsArrayStringCoercionAndInvalidJsonWhitespace(){
        for(String value:List.of(body.replace("[\""+ticket+"\"]","\"[\\\""+ticket+"\\\"]\""),
                body.replace("\"Không thể tham dự sự kiện.\"","[\"Không thể tham dự sự kiện.\"]"),
                body.replace("{","{\u00a0"),body.replace("[\""+ticket+"\"]","[]"),body.replace("]",",]")))
            assertThrows(RefundException.class,()->RefundRequestJson.parse(value));
    }
    @Test void requestAcceptsEscapedStringsAndAnyPropertyOrder(){
        var result=RefundRequestJson.parse("{\"reason\":\"Lý do có \\\"dấu ngoặc\\\" và \\u00e9.\",\"ticketIds\":[\""+ticket+"\"],\"orderId\":\""+order+"\"}");
        assertEquals("Lý do có \"dấu ngoặc\" và é.",result.reason());assertEquals(order,result.orderId());
    }
    @SuppressWarnings("unchecked") static <T>T proxy(Class<T> type,Bi work){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->work.run(m.getName(),a==null?new Object[0]:a));}
    interface Bi{Object run(String method,Object[]args)throws Throwable;}
    static class NoStore implements RefundRequestStore{public boolean active(UserPrincipal a){return true;}public List<RefundTicketOptionDto> tickets(UserPrincipal a,UUID o){return List.of();}public List<OrderOption> orders(UserPrincipal a){return List.of();}public RefundSummaryDto create(UserPrincipal a,CreateRefundRequestDto r,UUID k){throw new AssertionError();}public RefundPageDto list(UserPrincipal a,int p,int s){return new RefundPageDto(List.of(),0,p,s);}public RefundDetailDto detail(UserPrincipal a,UUID id){throw new AssertionError();}}
}
