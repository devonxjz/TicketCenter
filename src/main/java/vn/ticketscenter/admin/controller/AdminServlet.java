package vn.ticketscenter.admin.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(name = "AdminServlet", urlPatterns = {
        "/admin",
        "/admin/dashboard",
        "/admin/org-approval",
        "/admin/orgs/approval",
        "/admin/organizations",
        "/admin/organizations/list",
        "/admin/event-approval",
        "/admin/events/approval",
        "/admin/refund-approval",
        "/admin/refunds/approval",
        "/admin/settlements",
        "/admin/audit-logs"
})
public class AdminServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());

        String view;
        String activeTab;
        if (path.contains("org")) {
            view = "/WEB-INF/views/admin/organizations/list.jsp";
            activeTab = "org-approval";
        } else if (path.contains("event")) {
            view = "/WEB-INF/views/admin/event-approval.jsp";
            activeTab = "event-approval";
        } else if (path.contains("refund")) {
            resp.sendRedirect(req.getContextPath() + "/admin/refund-requests");
            return;
        } else if (path.contains("settlement")) {
            view = "/WEB-INF/views/admin/settlements.jsp";
            activeTab = "settlements";
        } else if (path.contains("audit")) {
            view = "/WEB-INF/views/admin/audit-logs.jsp";
            activeTab = "audit-logs";
        } else {
            view = "/WEB-INF/views/admin/dashboard.jsp";
            activeTab = "dashboard";
        }

        req.setAttribute("activeTab", activeTab);
        req.getRequestDispatcher(view).forward(req, resp);
    }
}
