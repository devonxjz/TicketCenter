package vn.ticketscenter.dashboard.controller;

import vn.ticketscenter.settlement.service.FinanceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import vn.ticketscenter.dashboard.service.DashboardService;
import vn.ticketscenter.dashboard.service.DashboardRuntime;
import vn.ticketscenter.report.controller.ReadsideWeb;

@WebServlet(name = "FinanceDashboardServlet", urlPatterns = "/api/admin/overview")
public final class DashboardServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        DashboardService service = DashboardRuntime.resolve(getServletContext());
        try {
            ReadsideWeb.json(response, service.admin(ReadsideWeb.actor(request)));
        } catch (FinanceException e) {
            ReadsideWeb.error(response, e);
        } catch (SQLException e) {
            ReadsideWeb.error(response, new FinanceException("OVERVIEW_UNAVAILABLE", 503));
        }
    }
}
