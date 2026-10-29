package vn.ticketscenter.audit.controller;

import vn.ticketscenter.settlement.service.FinanceException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.SQLException;
import vn.ticketscenter.audit.dto.AuditFilter;
import vn.ticketscenter.audit.service.AuditService;
import vn.ticketscenter.audit.service.AuditRuntime;
import vn.ticketscenter.report.controller.ReadsideWeb;
import vn.ticketscenter.report.dto.ReportFilter;

@WebServlet(
        name = "FinanceAuditServlet",
        urlPatterns = {"/api/admin/audit-logs", "/api/admin/audit-logs/*"})
public final class AuditServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        AuditService service = AuditRuntime.resolve(getServletContext());
        try {
            var actor = ReadsideWeb.actor(request);
            String path = request.getPathInfo();
            if (path == null || path.equals("/")) {
                ReadsideWeb.json(
                        response,
                        service.list(actor, AuditFilter.parse(ReadsideWeb.parameters(request))));
            } else {
                if (!path.matches("/[0-9a-fA-F-]{36}"))
                    throw new FinanceException("NOT_FOUND", 404);
                ReadsideWeb.json(
                        response, service.detail(actor, ReportFilter.uuid(path.substring(1))));
            }
        } catch (FinanceException e) {
            ReadsideWeb.error(response, e);
        } catch (SQLException e) {
            ReadsideWeb.error(response, new FinanceException("AUDIT_UNAVAILABLE", 503));
        }
    }
}
