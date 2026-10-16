package vn.ticketscenter.organizer.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

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
            resp.sendRedirect(req.getContextPath() + redirect);
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
                activeTab = "coupons";
            }
            case "/organizer/members" -> {
                view = "/WEB-INF/views/organizer/members.jsp";
                activeTab = "members";
            }
            default -> {
                view = "/WEB-INF/views/organizer/dashboard.jsp";
                activeTab = "dashboard";
            }
        }
        req.setAttribute("activeTab", activeTab);
        req.getRequestDispatcher(view).forward(req, resp);
    }
}
