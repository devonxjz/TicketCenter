package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import java.io.IOException;

/** Lets this feature coexist with future Event routes owned by another member. */
@WebFilter(filterName = "CheckInEventRoutesFilter", urlPatterns = "/api/events/*", dispatcherTypes = DispatcherType.REQUEST)
public final class CheckInEventRoutesFilter implements Filter {
    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest http = (HttpServletRequest) request;
        String path = http.getRequestURI().substring(http.getContextPath().length());
        String prefix = "/api/events/";
        String suffix = "/check-in-window";
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) { chain.doFilter(request, response); return; }
        String eventId = path.substring(prefix.length(), path.length() - suffix.length());
        if (eventId.contains("/")) { chain.doFilter(request, response); return; }
        if (!"GET".equals(http.getMethod())) {
            HttpServletResponse resp = (HttpServletResponse) response;
            resp.setHeader("Allow", "GET");
            HttpResponses.error(resp, 405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ phương thức GET.");
            return;
        }
        RequestDispatcher dispatcher = request.getServletContext().getNamedDispatcher("CheckInWindowServlet");
        if (dispatcher == null) {
            HttpResponses.error((HttpServletResponse) response, 503, "CHECK_IN_UNAVAILABLE", "Hệ thống soát vé chưa sẵn sàng.");
            return;
        }
        request.setAttribute(CheckInWindowServlet.EVENT_ID_ATTRIBUTE, eventId);
        dispatcher.forward(request, response);
    }
}
