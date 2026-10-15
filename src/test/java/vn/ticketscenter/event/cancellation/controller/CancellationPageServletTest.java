package vn.ticketscenter.event.cancellation.controller;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.*;
import java.lang.reflect.Proxy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.*;
import vn.ticketscenter.identity.security.*;
import static org.junit.jupiter.api.Assertions.*;
class CancellationPageServletTest {
 @Test void onlyAdminAndManagerCanOpenManagementPage() throws Exception {
  for(var actor:new UserPrincipal[]{new UserPrincipal(UUID.randomUUID(),PlatformRole.ADMIN,1),new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,1,OrganizationRole.MANAGER),new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,1,OrganizationRole.CHECK_IN_STAFF),new UserPrincipal(UUID.randomUUID(),PlatformRole.CUSTOMER,1)}){
   int[] status={200};boolean[] forwarded={false};
   var dispatcher=(RequestDispatcher)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{RequestDispatcher.class},(p,m,a)->{if(m.getName().equals("forward"))forwarded[0]=true;return null;});
   var request=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->switch(m.getName()){case "getAttribute"->SecurityContext.REQUEST_USER_ATTRIBUTE.equals(a[0])?actor:null;case "getRequestDispatcher"->dispatcher;default->null;});
   var response=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{if(m.getName().equals("sendError"))status[0]=(int)a[0];return null;});
   new CancellationPageServlet().doGet(request,response);
   assertEquals(actor.isManager()?200:403,status[0]);assertEquals(actor.isManager(),forwarded[0]);
  }
 }
}
