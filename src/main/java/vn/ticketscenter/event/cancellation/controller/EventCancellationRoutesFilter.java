package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.DatabaseActorFilter;

@WebFilter(urlPatterns={"/api/admin/events/*","/api/events/*"})
public final class EventCancellationRoutesFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {
        var req=(HttpServletRequest)request;var path=req.getRequestURI().substring(req.getContextPath().length());
        var pieces=path.split("/",-1);
        boolean review=(pieces.length==5 && pieces[2].equals("events") && pieces[4].equals("cancellation-request"))
            || (pieces.length==6 && pieces[2].equals("admin") && pieces[3].equals("events")
                && java.util.Set.of("cancellation-request","cancellation-approve","cancellation-reject").contains(pieces[5]));
        if(review) {
            boolean read=pieces[pieces.length-1].equals("cancellation-request") && req.getMethod().equals("GET");
            if(!read && !req.getMethod().equals("POST")) {
                ((HttpServletResponse)response).setHeader("Allow",pieces[pieces.length-1].equals("cancellation-request")?"GET, POST":"POST");
                HttpResponses.error((HttpServletResponse)response,405,"METHOD_NOT_ALLOWED","Phương thức không được hỗ trợ.");return;
            }
            DatabaseActorFilter.withActor(request,response,(r,s)->req.getServletContext().getNamedDispatcher("CancellationReviewServlet").forward(r,s));return;
        }
        if(pieces.length==6&&(pieces[5].equals("cancel")||pieces[5].equals("cancellation-progress"))) {
            boolean cancel=pieces[5].equals("cancel");
            if(!req.getMethod().equals(cancel?"POST":"GET")) {
                ((HttpServletResponse)response).setHeader("Allow",cancel?"POST":"GET");
                HttpResponses.error((HttpServletResponse)response,405,"METHOD_NOT_ALLOWED","Phương thức không được hỗ trợ.");return;
            }
            DatabaseActorFilter.withActor(request,response,(r,s)->req.getServletContext()
                    .getNamedDispatcher(cancel?"AdminEventCancelServlet":"AdminCancellationProgressServlet").forward(r,s));
        }else chain.doFilter(request,response);
    }
}
