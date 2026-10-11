package vn.ticketscenter.report.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrganizationReadsideRoutesFilterTest {
    @Test void realOrganizationReadRoutesUseNamedDispatchInEitherContext() throws Exception {
        for(String context:List.of("","/ticketscenter"))for(String suffix:List.of("reports","overview","overview/extra")) {
            var call=new Call(context,"GET","/api/organizations/"+UUID.randomUUID()+"/"+suffix,true);
            call.run();assertTrue(call.forwarded);assertFalse(call.chained);
            assertEquals(call.path.substring("/api/organizations".length()),call.attributes.get(OrganizationReadsideRoutesFilter.ROUTE_ATTRIBUTE));
        }
    }
    @Test void eventCreationAndOtherOwnerRoutesContinueUnchanged() throws Exception {
        for(String method:List.of("GET","POST"))for(String suffix:List.of("events","members","reports-extra")) {
            var call=new Call("/ticketscenter",method,"/api/organizations/"+UUID.randomUUID()+"/"+suffix,true);
            call.run();assertTrue(call.chained);assertFalse(call.forwarded);assertTrue(call.attributes.isEmpty());
        }
    }
    @Test void mutationsOfReadRoutesAreRejectedAndUnavailableDispatcherFailsClosed() throws Exception {
        var post=new Call("","POST","/api/organizations/"+UUID.randomUUID()+"/overview",true);
        post.run();assertEquals(405,post.status);assertEquals("GET",post.headers.get("Allow"));assertFalse(post.forwarded);assertFalse(post.chained);
        var missing=new Call("","GET","/api/organizations/"+UUID.randomUUID()+"/reports",false);
        missing.run();assertEquals(503,missing.status);assertFalse(missing.chained);assertFalse(missing.forwarded);
    }
    static class Call {
        final String context,method,path;final boolean available;
        final Map<String,Object> attributes=new HashMap<>();final Map<String,String> headers=new HashMap<>();
        boolean forwarded,chained;int status;
        Call(String context,String method,String path,boolean available){this.context=context;this.method=method;this.path=path;this.available=available;}
        void run()throws Exception {
            var dispatcher=proxy(RequestDispatcher.class,(name,args)->{if(name.equals("forward"))forwarded=true;return null;});
            var servletContext=proxy(ServletContext.class,(name,args)->{assertEquals("getNamedDispatcher",name);assertEquals("FinanceReportServlet",args[0]);return available?dispatcher:null;});
            var request=proxy(HttpServletRequest.class,(name,args)->switch(name){
                case "getRequestURI"->context+path;case "getContextPath"->context;case "getMethod"->method;case "getServletContext"->servletContext;
                case "setAttribute"->{attributes.put((String)args[0],args[1]);yield null;}default->null;
            });
            var response=proxy(HttpServletResponse.class,(name,args)->switch(name){
                case "isCommitted"->false;case "setStatus"->{status=(int)args[0];yield null;}case "setHeader"->{headers.put((String)args[0],(String)args[1]);yield null;}
                case "getWriter"->new PrintWriter(new StringWriter());default->null;
            });
            new OrganizationReadsideRoutesFilter().doFilter(request,response,(req,res)->chained=true);
        }
    }
    interface Invoke {Object call(String name,Object[] args)throws Throwable;}
    @SuppressWarnings("unchecked")static <T>T proxy(Class<T> type,Invoke invoke){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},(p,m,a)->invoke.call(m.getName(),a));}
}
