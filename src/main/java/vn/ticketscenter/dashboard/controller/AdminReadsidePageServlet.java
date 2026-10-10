package vn.ticketscenter.dashboard.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import vn.ticketscenter.report.controller.ReadsideHttp;
import vn.ticketscenter.settlement.infrastructure.*;

/** Admin pages have their own authorization boundary; manager reports remain separate. */
@WebServlet(name="FinanceAdminReadsidePageServlet", urlPatterns={"/finance/audit", "/finance/overview"})
public final class AdminReadsidePageServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String view = switch (request.getServletPath()) {
                case "/finance/audit" -> "audit/audit.jsp";
                case "/finance/overview" -> "dashboard/overview.jsp";
                default -> throw new FinanceException("NOT_FOUND", 404);
            };
            var actor = ReadsideHttp.actor(request);
            try (var db = FinanceRuntime.database(getServletContext()).open()) {
                FinanceAuthorization.requireAdmin(db, actor);
            }
            request.setAttribute("financeOrganizationId", "");
            request.setAttribute("financeAdmin", true);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            request.getRequestDispatcher("/WEB-INF/views/" + view).forward(request, response);
        } catch (FinanceException e) {
            ReadsideHttp.error(response, e);
        } catch (SQLException e) {
            ReadsideHttp.error(response, new FinanceException("FINANCE_UNAVAILABLE", 503));
        }
    }
}
