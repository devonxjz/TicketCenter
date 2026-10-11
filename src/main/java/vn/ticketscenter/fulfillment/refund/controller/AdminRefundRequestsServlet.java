package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.SecurityContext;

@WebServlet(name="AdminRefundRequestsServlet",urlPatterns={"/api/admin/refund-requests","/api/admin/refund-requests/*","/admin/refund-requests","/admin/refund-requests/*"})
public final class AdminRefundRequestsServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException,ServletException {
        try {
            var actor=AdminRefundWeb.actor(request);var service=AdminRefundRuntime.resolve(request.getServletContext());
            String path=RefundWeb.path(request);boolean api=path.startsWith("/api/");String prefix=api?"/api/admin/refund-requests":"/admin/refund-requests";
            response.setHeader("Cache-Control","no-store");
            if(path.equals(prefix)) {
                int page=RefundWeb.page(request.getParameter("page"),1),size=RefundWeb.page(request.getParameter("pageSize"),20);
                var result=service.list(actor,request.getParameter("status"),page,size);
                if(api){HttpResponses.data(response,result.json());return;}
                request.setAttribute("refundPageJson",result.json());request.setAttribute("statusFilter",request.getParameter("status"));
                request.getRequestDispatcher("/WEB-INF/views/admin/refunds/list.jsp").forward(request,response);
            }else {
                var result=service.detail(actor,RefundWeb.id(path.substring(prefix.length()+1)));
                if(api){HttpResponses.data(response,result.json());return;}
                request.setAttribute("refundId",result.refund().refundId().toString());request.setAttribute("csrf",SecurityContext.getOrCreateCsrfToken(request));
                request.getRequestDispatcher("/WEB-INF/views/admin/refunds/review.jsp").forward(request,response);
            }
        }catch(RuntimeException failure){RefundWeb.error(response,failure);}
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException,ServletException {
        try {
            String path=RefundWeb.path(request);String servlet;
            if(path.endsWith("/decision")){AdminRefundWeb.target(request,"decision");servlet="AdminRefundDecisionServlet";}
            else if(path.endsWith("/retry")){AdminRefundWeb.target(request,"retry");servlet="AdminRefundRetryServlet";}
            else throw new RefundException(405,"METHOD_NOT_ALLOWED","Đường dẫn này không nhận thao tác ghi.");
            request.getServletContext().getNamedDispatcher(servlet).forward(request,response);
        }catch(RuntimeException failure){RefundWeb.error(response,failure);}
    }
}
