package vn.ticketscenter.event.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.identity.security.SecurityContext;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Event-owned page aliases; all private API data still requires current DB membership/role. */
@WebServlet(
        name = "EventManagementPagesServlet",
        urlPatterns = {"/events/manage", "/events/manage/editor", "/events/review"})
public class EventManagementPagesServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        var user = SecurityContext.getCurrentUser(request);
        if (user.isEmpty()) {
            String path =
                    request.getRequestURI()
                            + (request.getQueryString() == null
                                    ? ""
                                    : "?" + request.getQueryString());
            response.sendRedirect(
                    request.getContextPath()
                            + "/auth/login?redirect="
                            + URLEncoder.encode(path, StandardCharsets.UTF_8));
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        response.setHeader("Cache-Control", "no-store");
        if (path.equals("/events/review") && !user.get().isAdmin()) {
            vn.ticketscenter.config.web.HttpResponses.error(
                    response, 403, "EVENT_FORBIDDEN", "Chỉ quản trị viên được thẩm định sự kiện.");
            return;
        }
        String view =
                path.equals("/events/review")
                        ? "/WEB-INF/views/admin/event-approval.jsp"
                        : path.endsWith("/editor")
                                ? "/WEB-INF/views/organizer/event-editor.jsp"
                                : "/WEB-INF/views/organizer/events.jsp";
        request.getRequestDispatcher(view).forward(request, response);
    }
}
