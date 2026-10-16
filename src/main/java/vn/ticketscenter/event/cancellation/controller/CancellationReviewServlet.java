package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.persistence.*;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.cancellation.service.*;
import vn.ticketscenter.event.dao.EventCancellationRepository;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.SecurityContext;

@WebServlet(name="CancellationReviewServlet",urlPatterns="/api/event-cancellation-review-internal")
public final class CancellationReviewServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {handle(request,response);}
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException {handle(request,response);}
    private void handle(HttpServletRequest request,HttpServletResponse response)throws IOException {
        request.setCharacterEncoding("UTF-8");response.setHeader("Cache-Control","no-store");
        try {
            String[] path=request.getRequestURI().substring(request.getContextPath().length()).split("/",-1);
            boolean admin=path.length==6 && path[2].equals("admin") && path[3].equals("events");
            boolean manager=path.length==5 && path[2].equals("events");
            if(!admin && !manager)throw new RefundException(404,"ROUTE_NOT_FOUND","Đường dẫn không tồn tại.");
            var actor=SecurityContext.getCurrentUser(request).orElseThrow(()->new RefundException(401,"AUTH_REQUIRED","Cần đăng nhập."));
            if(admin && !actor.isAdmin())throw new RefundException(403,"ADMIN_REQUIRED","Cần quyền quản trị viên.");
            String endpoint=path[path.length-1];
            String action=request.getMethod().equals("GET")?"READ":switch(endpoint){case "cancellation-request"->"REQUEST";case "cancellation-approve"->"APPROVE";case "cancellation-reject"->"REJECT";default->throw EventCancellationWeb.invalid();};
            if((action.equals("READ") || action.equals("REQUEST")) && !endpoint.equals("cancellation-request"))throw EventCancellationWeb.invalid();
            var eventId=EventCancellationWeb.id(path[admin?4:3]);
            java.util.UUID key=null;String reason=null;
            if(!action.equals("READ")) {
                key=EventCancellationWeb.mutation(request);
                if(action.equals("APPROVE")) {
                    var body=new StringBuilder();char[] buffer=new char[256];int n;
                    try(var reader=request.getReader()){while((n=reader.read(buffer))!=-1){body.append(buffer,0,n);if(body.length()>8192)throw new RefundException(413,"PAYLOAD_TOO_LARGE","Nội dung yêu cầu quá lớn.");}}
                    if(!body.toString().strip().equals("{}"))throw EventCancellationWeb.invalid();
                }else reason=EventCancellationWeb.body(request).reason();
            }
            Object configured=request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if(!(configured instanceof PersistenceRegistry registry))throw new RefundException(503,"DATABASE_UNAVAILABLE","Hệ thống hủy chưa sẵn sàng.");
            var cache=new CancellationProgressCacheHelper();
            var service=new CancellationReviewService(new CancellationReviewRepository(registry.transactionManager()),id->{
                // Run only after commit; cache outage cannot turn an approved write into failure.
                var projection=new EventCancellationRepository(registry.transactionManager()).progress(actor,id);
                cache.cancelled(projection);
            });
            HttpResponses.data(response,service.execute(actor,eventId,action,reason,key));
        }catch(RuntimeException failure){EventCancellationWeb.error(response,failure);}
    }
}
