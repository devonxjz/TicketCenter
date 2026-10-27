package vn.ticketscenter.dashboard.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import vn.ticketscenter.dashboard.service.DashboardService;
import vn.ticketscenter.dashboard.service.DashboardServiceImpl;
import vn.ticketscenter.dashboard.cache.DashboardCache;
import vn.ticketscenter.report.controller.ReadsideHttp;
import vn.ticketscenter.settlement.infrastructure.*;

@WebServlet(name = "FinanceDashboardServlet", urlPatterns = "/api/admin/overview")
public final class DashboardServlet extends HttpServlet {
    private DashboardService service;

    @Override
    public void init() {
        service =
                new DashboardServiceImpl(
                        FinanceRuntime.database(getServletContext()), DashboardCache.configured());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            ReadsideHttp.json(response, service.admin(ReadsideHttp.actor(request)));
        } catch (FinanceException e) {
            ReadsideHttp.error(response, e);
        } catch (SQLException e) {
            ReadsideHttp.error(response, new FinanceException("OVERVIEW_UNAVAILABLE", 503));
        }
    }
}
