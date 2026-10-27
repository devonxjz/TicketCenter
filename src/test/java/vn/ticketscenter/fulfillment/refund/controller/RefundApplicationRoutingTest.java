package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.service.SessionValidator;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.config.web.OrganizationApiRoutesFilter;
import static org.junit.jupiter.api.Assertions.*;
import static vn.ticketscenter.fulfillment.refund.controller.RefundRequestServletsTest.proxy;

class RefundApplicationRoutingTest {
    @Test void descriptorAndAnnotationsHaveNoDuplicateServletMappings()throws Exception{
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var document=factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/web.xml").toFile());
        assertEquals("false",document.getDocumentElement().getAttribute("metadata-complete"));
        Map<String,String> mappings=new HashMap<>();Set<String> descriptorOwners=new HashSet<>();
        var configured=document.getElementsByTagName("servlet-mapping");
        for(int i=0;i<configured.getLength();i++){
            var mapping=(org.w3c.dom.Element)configured.item(i);String name=mapping.getElementsByTagName("servlet-name").item(0).getTextContent().strip();descriptorOwners.add(name);
            var paths=mapping.getElementsByTagName("url-pattern");for(int p=0;p<paths.getLength();p++)assertNull(mappings.put(paths.item(p).getTextContent().strip(),name));
        }
        Path root=Path.of("src/main/java");
        try(var files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){
            String type=root.relativize(file).toString().replace('\\','.').replace('/','.').replaceFirst("\\.java$","");
            var annotation=Class.forName(type,false,getClass().getClassLoader()).getAnnotation(WebServlet.class);if(annotation==null)continue;
            String owner=annotation.name().isEmpty()?type:annotation.name();if(descriptorOwners.contains(owner))continue;
            for(String path:annotation.urlPatterns())assertNull(mappings.put(path,owner),()->"Duplicate production mapping "+path+" for "+owner);
            for(String path:annotation.value())assertNull(mappings.put(path,owner),()->"Duplicate production mapping "+path+" for "+owner);
        }}
        assertEquals("EventApiServlet",mappings.get("/api/organizations/*"));assertEquals("FinanceReportServlet",mappings.get("/api/admin/reports"));
        assertEquals("RefundRequestServlet",mappings.get("/api/refund-requests"));
    }
    @Test void onlyReportsSubresourceForwardsWithOriginalReportRouteContract()throws Exception{
        var fixture=new RefundRequestServletsTest();fixture.login();fixture.method="GET";fixture.path="/api/organizations/"+fixture.order+"/reports";
        var context=proxy(ServletContext.class,(method,args)->{
            if(method.equals("getNamedDispatcher")){assertEquals("FinanceReportServlet",args[0]);return proxy(RequestDispatcher.class,(name,values)->{if(name.equals("forward")){assertEquals(vn.ticketscenter.identity.security.SecurityContext.requireCurrentUser(fixture.request()).actorId(),vn.ticketscenter.config.persistence.DatabaseActorContext.current());var routed=(HttpServletRequest)values[0];assertEquals("/api/organizations",routed.getServletPath());assertEquals("/"+fixture.order+"/reports",routed.getPathInfo());assertEquals("/tc"+fixture.path,routed.getRequestURI());fixture.forwarded="reports";}return null;});}
            if(method.equals("getAttribute"))return fixture.contextAttributes.get(args[0]);return null;
        });
        var request=new HttpServletRequestWrapper(fixture.request()){@Override public ServletContext getServletContext(){return context;}};
        var filter=new OrganizationApiRoutesFilter();filter.doFilter(request,fixture.response(),(r,s)->fail("Report route reached event servlet"));assertEquals("reports",fixture.forwarded);
        assertEquals("/"+fixture.order+"/reports",fixture.attributes.get(vn.ticketscenter.report.controller.OrganizationReadsideRoutesFilter.ROUTE_ATTRIBUTE));
        fixture.path="/api/organizations/"+fixture.order+"/events";boolean[] passed={false};filter.doFilter(request,fixture.response(),(r,s)->passed[0]=true);assertTrue(passed[0]);
    }
    @Test void refundForwardHasActorAndRestoresOuterContextEvenOnFailure()throws Exception {
        var fixture=new RefundRequestServletsTest();fixture.login();fixture.method="GET";fixture.path="/refund-request";
        UUID outer=UUID.randomUUID();vn.ticketscenter.config.persistence.DatabaseActorContext.set(outer);
        var context=proxy(ServletContext.class,(method,args)->method.equals("getNamedDispatcher")?
                proxy(RequestDispatcher.class,(name,values)->{if(name.equals("forward")){
                    assertEquals(vn.ticketscenter.identity.security.SecurityContext.requireCurrentUser(fixture.request()).actorId(),vn.ticketscenter.config.persistence.DatabaseActorContext.current());
                    throw new ServletException("fixture failure");
                }return null;}):null);
        var request=new HttpServletRequestWrapper(fixture.request()){@Override public ServletContext getServletContext(){return context;}};
        try {
            assertThrows(ServletException.class,()->new RefundOrderRoutesFilter().doFilter(request,fixture.response(),(r,s)->fail()));
            assertEquals(outer,vn.ticketscenter.config.persistence.DatabaseActorContext.current());
        }finally{vn.ticketscenter.config.persistence.DatabaseActorContext.clear();}
    }
    @Test void reportsForwardingChecksSessionEvenBeforeSharedFilters()throws Exception{
        var f=new RefundRequestServletsTest();f.method="GET";f.path="/api/organizations/"+f.order+"/reports";var filter=new OrganizationApiRoutesFilter();
        filter.doFilter(f.request(),f.response(),(r,s)->fail());assertEquals(401,f.status);
        f.login();f.sessionValid=false;filter.doFilter(f.request(),f.response(),(r,s)->fail());assertEquals(401,f.status);
        f.login();f.sessionValid=true;f.contextAttributes.remove(IdentityServices.SESSION_VALIDATOR_ATTRIBUTE);filter.doFilter(f.request(),f.response(),(r,s)->fail());assertEquals(503,f.status);
    }
    @Test void reportSubresourceRejectsMutationsWithoutTouchingBusinessLogic()throws Exception{
        var f=new RefundRequestServletsTest();f.path="/api/organizations/"+f.order+"/reports";
        new OrganizationApiRoutesFilter().doFilter(f.request(),f.response(),(r,s)->fail());assertEquals(405,f.status);assertEquals("GET",f.responseHeaders.get("Allow"));
    }
}
