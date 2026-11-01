package vn.ticketscenter.report.controller;

import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.service.FinanceException;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.report.service.*;
import vn.ticketscenter.dashboard.service.DashboardService;
import vn.ticketscenter.dashboard.service.DashboardRuntime;

@WebServlet(
        name = "FinanceReportServlet",
        urlPatterns = {"/api/admin/reports", "/api/reports/export", "/api/reports/options"})
public final class ReportServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ReportService reports = ReportRuntime.resolve(getServletContext());
        DashboardService overview = DashboardRuntime.resolve(getServletContext());
        try {
            UserPrincipal actor = ReadsideWeb.actor(request);
            Map<String, String> params = ReadsideWeb.parameters(request);
            String path = request.getServletPath();
            if (path.equals("/api/reports/options")) {
                ReadsideWeb.json(
                        response,
                        reports.options(actor, ReportFilter.uuid(params.get("organizationId"))));
                return;
            }
            UUID org = null;
            Object organizationRoute =
                    request.getAttribute(OrganizationReadsideRoutesFilter.ROUTE_ATTRIBUTE);
            if (organizationRoute != null) {
                Object[] route = ReadsideWeb.organizationRoute((String) organizationRoute);
                org = (UUID) route[0];
                if (route[1].equals("overview")) {
                    if (!params.isEmpty()) {
                        throw new FinanceException("INVALID_FILTER", 400);
                    }
                    try {
                        ReadsideWeb.json(response, overview.organization(actor, org));
                    } catch (SQLException e) {
                        throw new FinanceException("OVERVIEW_UNAVAILABLE", 503);
                    }
                    return;
                }
            }
            ReportFilter filter = ReportFilter.parse(params, org);
            if (path.equals("/api/reports/export")) {
                response.setContentType("text/csv;charset=UTF-8");
                response.setHeader(
                        "Content-Disposition",
                        "attachment; filename=\"ticketscenter-"
                                + filter.metric().name().toLowerCase(Locale.ROOT)
                                + ".csv\"");
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                reports.export(actor, filter, response.getOutputStream());
            } else {
                ReadsideWeb.json(
                        response,
                        path.equals("/api/admin/reports")
                                ? reports.jsonAdmin(actor, filter)
                                : reports.json(actor, filter));
            }
        } catch (FinanceException e) {
            ReadsideWeb.error(response, e);
        } catch (SQLException e) {
            ReadsideWeb.error(response, new FinanceException("REPORT_UNAVAILABLE", 503));
        }
    }
}
