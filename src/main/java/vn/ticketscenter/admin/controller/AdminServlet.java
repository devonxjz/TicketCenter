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

        // Exact routing: pages that moved to another module redirect there instead of rendering a local view.
        String redirect = switch (path) {
            case "/admin", "/admin/dashboard" -> "/finance/overview";
            case "/admin/settlements" -> "/finance/settlements";
            case "/admin/audit-logs" -> "/finance/audit";
            case "/admin/refund-approval", "/admin/refunds/approval" -> "/admin/refund-requests";
            default -> null;
        };
        if (redirect != null) {
            resp.sendRedirect(req.getContextPath() + redirect);
            return;
        }
        String view;
        String activeTab;
        if (path.equals("/admin/event-approval") || path.equals("/admin/events/approval")) {
            view = "/WEB-INF/views/admin/event-approval.jsp";
            activeTab = "event-approval";
        } else {
            view = "/WEB-INF/views/admin/organizations/list.jsp";
            activeTab = "org-approval";
        }
        req.setAttribute("activeTab", activeTab);
        req.getRequestDispatcher(view).forward(req, resp);
    }
}
