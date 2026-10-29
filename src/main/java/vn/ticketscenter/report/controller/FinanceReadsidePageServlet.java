package vn.ticketscenter.report.controller;

import vn.ticketscenter.settlement.service.FinanceException;
import vn.ticketscenter.settlement.service.FinanceRuntime;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.UUID;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.dao.FinanceAuthorization;

@WebServlet(
        name = "FinanceReadsidePageServlet",
        urlPatterns = {"/finance/reports"})
public final class FinanceReadsidePageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            var actor = ReadsideWeb.actor(request);
            String path = request.getServletPath();
            UUID org = ReportFilter.uuid(ReadsideWeb.parameters(request).get("organizationId"));
            boolean admin = true;
            try (var db = FinanceRuntime.database(getServletContext()).open()) {
                if (!path.equals("/finance/reports")) throw new FinanceException("NOT_FOUND", 404);
                else {
                    try {
                        FinanceAuthorization.requireAdmin(db, actor);
                    } catch (FinanceException e) {
                        if (e.status() != 403) throw e;
                        admin = false;
                        if (org == null) {
                            org = FinanceAuthorization.firstManagedOrganization(db, actor);
                        }
                        if (org == null) throw new FinanceException("FORBIDDEN", 403);
                        FinanceAuthorization.requireManager(db, actor, org);
                    }
                }
            }
            request.setAttribute("financeOrganizationId", org == null ? "" : org.toString());
            request.setAttribute("financeAdmin", admin);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            String view = "report/reports.jsp";
            request.getRequestDispatcher("/WEB-INF/views/" + view).forward(request, response);
        } catch (FinanceException e) {
            ReadsideWeb.error(response, e);
        } catch (SQLException e) {
            ReadsideWeb.error(response, new FinanceException("FINANCE_UNAVAILABLE", 503));
        }
    }
}
