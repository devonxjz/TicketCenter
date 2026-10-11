package vn.ticketscenter.settlement.controller;

import org.junit.jupiter.api.Test;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.io.*;
import java.util.*;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.identity.model.PlatformRole;
import static org.junit.jupiter.api.Assertions.*;

class AdminFinanceServletTest {
    @Test void unauthenticatedRequestHasSafeEnvelopeAndNoDatabaseUse() throws Exception {
        var result=call(null,null,"GET","/api/admin/events/00000000-0000-0000-0000-000000000001/settlement",null);
        assertEquals(401,result.status); assertTrue(result.body.toString().contains("UNAUTHENTICATED")); assertFalse(result.body.toString().contains("null"));
    }
    @Test void mutationWithoutCsrfIsRejectedBeforeDatabaseUse() throws Exception {
        var actor=new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0);
        var result=call(actor,null,"POST","/api/admin/settlements/00000000-0000-0000-0000-000000000001/confirm",null);
        assertEquals(403,result.status); assertTrue(result.body.toString().contains("CSRF_INVALID"));
    }
    @Test void unknownFinancialRouteReturnsNotFound() throws Exception {
        var result=call(new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,0),null,"GET","/api/admin/events/invalid/other",null);
        assertEquals(404,result.status);
    }
    static Result call(UserPrincipal actor,String csrf,String method,String uri,String key) throws Exception {
        var result=new Result();
        HttpServletRequest request=proxy(HttpServletRequest.class,(name,args)->switch(name) {
            case "getMethod" -> method; case "getRequestURI" -> uri; case "getContextPath" -> "";
            case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0])?actor:null;
            case "getHeader" -> "Idempotency-Key".equals(args[0])?key:null;
            case "getSession" -> null;
            case "getProtocol" -> "HTTP/1.1";
            default -> null;
        });
        HttpServletResponse response=proxy(HttpServletResponse.class,(name,args)-> {
            if(name.equals("setStatus")) result.status=(Integer)args[0];
            if(name.equals("getWriter")) return new PrintWriter(result.body);
            return null;
        });
        new AdminFinanceServlet().service((ServletRequest)request,(ServletResponse)response); return result;
    }
    static class Result { int status=200; StringWriter body=new StringWriter(); }
    interface Call { Object invoke(String name,Object[] args) throws Throwable; }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type,Call call) { return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->call.invoke(m.getName(),a)); }
}
