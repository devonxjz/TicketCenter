package vn.ticketscenter.organizer.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import vn.ticketscenter.config.web.ApiRequest;

@WebServlet(
        name = "OrganizerServlet",
        urlPatterns = {
            "/organizer",
            "/organizer/dashboard",
            "/organizer/events",
            "/organizer/events/new",
            "/organizer/event-editor",
            "/organizer/coupons",
            "/organizer/members",
            "/organizer/checkin",
            "/organizer/checkin-select",
            "/organizer/checkin/select",
            "/organizer/checkin-scanner",
            "/organizer/checkin/scanner",
            "/organizer/reports"
        })
public class OrganizerServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = ApiRequest.path(request);

        // Check-in and reports are served by their own modules; every view here loads its data from
        // the API.
        String redirect =
                switch (path) {
                    case "/organizer/checkin",
                            "/organizer/checkin-select",
                            "/organizer/checkin/select" ->
                            "/check-ins";
                    case "/organizer/checkin-scanner", "/organizer/checkin/scanner" ->
                            "/check-ins/scanner";
                    case "/organizer/reports" -> "/finance/reports";
                    default -> null;
                };
        if (redirect != null) {
            response.sendRedirect(request.getContextPath() + redirect);
            return;
        }
        String view;
        String activeTab;
        switch (path) {
            case "/organizer/events" -> {
                view = "/WEB-INF/views/organizer/events.jsp";
                activeTab = "events";
            }
            case "/organizer/events/new", "/organizer/event-editor" -> {
                view = "/WEB-INF/views/organizer/event-editor.jsp";
                activeTab = "event-editor";
            }
            case "/organizer/coupons" -> {
                view = "/WEB-INF/views/organizer/coupons.jsp";
                response.setHeader("Cache-Control", "no-store");
                activeTab = "coupons";
            }
            case "/organizer/members" -> {
                view = "/WEB-INF/views/organizer/members.jsp";
                activeTab = "members";
            }
            default -> {
                view = "/WEB-INF/views/organizer/dashboard.jsp";
                response.setHeader("Cache-Control", "no-store");
                activeTab = "dashboard";
            }
        }
        request.setAttribute("activeTab", activeTab);
        request.getRequestDispatcher(view).forward(request, response);
    }
}
