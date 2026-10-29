package vn.ticketscenter.event.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.LoginRedirect;
import vn.ticketscenter.identity.security.SecurityContext;
import java.io.IOException;

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
            LoginRedirect.send(request, response);
            return;
        }
        String path = ApiRequest.path(request);
        response.setHeader("Cache-Control", "no-store");
        if (path.equals("/events/review") && !user.get().isAdmin()) {
            HttpResponses.error(
                    response, 403, "EVENT_FORBIDDEN", "Chỉ quản trị viên được thẩm định sự kiện.");
            return;
        }

        // set URL to the matching view
        String view;
        if (path.equals("/events/review")) {
            view = "/WEB-INF/views/admin/event-approval.jsp";
        } else if (path.endsWith("/editor")) {
            view = "/WEB-INF/views/organizer/event-editor.jsp";
        } else {
            view = "/WEB-INF/views/organizer/events.jsp";
        }
        request.getRequestDispatcher(view).forward(request, response);
    }
}
