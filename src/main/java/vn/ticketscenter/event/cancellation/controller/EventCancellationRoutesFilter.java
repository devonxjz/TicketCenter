package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.DatabaseActorFilter;

@WebFilter(urlPatterns="/api/admin/events/*")
public final class EventCancellationRoutesFilter implements Filter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException {
        var req=(HttpServletRequest)request;var path=req.getRequestURI().substring(req.getContextPath().length());
        var pieces=path.split("/",-1);
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
