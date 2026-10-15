package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.identity.security.SecurityContext;

@WebServlet(name="CancellationPageServlet",urlPatterns="/events/cancellation")
public final class CancellationPageServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException,ServletException {
        var actor=SecurityContext.getCurrentUser(request);
        if(actor.isEmpty()){response.sendRedirect(request.getContextPath()+"/auth/login?redirect="+java.net.URLEncoder.encode(request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString()),java.nio.charset.StandardCharsets.UTF_8));return;}
        if(!actor.get().isManager()){response.sendError(HttpServletResponse.SC_FORBIDDEN);return;}
        request.setAttribute("cancellationAdmin",actor.get().isAdmin());response.setHeader("Cache-Control","no-store");
        request.getRequestDispatcher("/WEB-INF/views/event/cancellation.jsp").forward(request,response);
    }
}
