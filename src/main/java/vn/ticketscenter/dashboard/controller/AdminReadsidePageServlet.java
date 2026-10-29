package vn.ticketscenter.dashboard.controller;

import java.sql.Connection;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.service.FinanceException;
import vn.ticketscenter.settlement.service.FinanceRuntime;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.SQLException;
import vn.ticketscenter.report.controller.ReadsideWeb;
import vn.ticketscenter.settlement.dao.FinanceAuthorization;

/** Admin pages have their own authorization boundary; manager reports remain separate. */
@WebServlet(
        name = "FinanceAdminReadsidePageServlet",
        urlPatterns = {"/finance/audit", "/finance/overview"})
public final class AdminReadsidePageServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String view =
                    switch (request.getServletPath()) {
                        case "/finance/audit" -> "audit/audit.jsp";
                        case "/finance/overview" -> "dashboard/overview.jsp";
                        default -> throw new FinanceException("NOT_FOUND", 404);
                    };
            UserPrincipal actor = ReadsideWeb.actor(request);
            try (Connection db = FinanceRuntime.database(getServletContext()).open()) {
                FinanceAuthorization.requireAdmin(db, actor);
            }
            request.setAttribute("financeOrganizationId", "");
            request.setAttribute("financeAdmin", true);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            request.getRequestDispatcher("/WEB-INF/views/" + view).forward(request, response);
        } catch (FinanceException e) {
            ReadsideWeb.error(response, e);
        } catch (SQLException e) {
            ReadsideWeb.error(response, new FinanceException("FINANCE_UNAVAILABLE", 503));
        }
    }
}
