package vn.ticketscenter.report.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.UUID;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.*;

@WebServlet(
        name = "FinanceReadsidePageServlet",
        urlPatterns = {"/finance/reports"})
public final class FinanceReadsidePageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            var actor = ReadsideHttp.actor(request);
            String path = request.getServletPath();
            UUID org = ReportFilter.uuid(ReadsideHttp.parameters(request).get("organizationId"));
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
                            try (var query =
                                    db.prepareStatement(
                                            "SELECT TOP (1) organization_id FROM dbo.vw_ActiveManagerMemberships WHERE user_id=? ORDER BY organization_id")) {
                                query.setString(1, actor.actorId().toString());
                                try (var row = query.executeQuery()) {
                                    if (row.next()) org = UUID.fromString(row.getString(1));
                                }
                            }
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
            ReadsideHttp.error(response, e);
        } catch (SQLException e) {
            ReadsideHttp.error(response, new FinanceException("FINANCE_UNAVAILABLE", 503));
        }
    }
}
