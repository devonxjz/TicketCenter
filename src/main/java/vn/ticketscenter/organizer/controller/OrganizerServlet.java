package vn.ticketscenter.organizer.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "OrganizerServlet", urlPatterns = {
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        String view;
        String activeTab;
        if (path.contains("scanner")) {
            view = "/WEB-INF/views/organizer/checkin-scanner.jsp";
            activeTab = "checkin-scanner";
        } else if (path.contains("checkin")) {
            view = "/WEB-INF/views/organizer/checkin-select.jsp";
            activeTab = "checkin";
        } else if (path.endsWith("event-editor") || path.endsWith("/new")) {
            view = "/WEB-INF/views/organizer/event-editor.jsp";
            activeTab = "event-editor";
        } else if (path.contains("events")) {
            view = "/WEB-INF/views/organizer/events.jsp";
            activeTab = "events";
        } else if (path.contains("coupons")) {
            view = "/WEB-INF/views/organizer/coupons.jsp";
            activeTab = "coupons";
        } else if (path.contains("members")) {
            view = "/WEB-INF/views/organizer/members.jsp";
            activeTab = "members";
        } else if (path.contains("reports")) {
            view = "/WEB-INF/views/organizer/reports.jsp";
            activeTab = "reports";
        } else {
            view = "/WEB-INF/views/organizer/dashboard.jsp";
            activeTab = "dashboard";
        }

        req.setAttribute("activeTab", activeTab);
        req.getRequestDispatcher(view).forward(req, resp);
    }
}
