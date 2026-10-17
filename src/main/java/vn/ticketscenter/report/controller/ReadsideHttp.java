package vn.ticketscenter.report.controller;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import vn.ticketscenter.identity.security.*;
import vn.ticketscenter.report.dto.ReportFilter;
import vn.ticketscenter.settlement.infrastructure.*;

public final class ReadsideHttp {
    private ReadsideHttp() {}

    public static Map<String, String> parameters(HttpServletRequest request) {
        var result = new HashMap<String, String>();
        request.getParameterMap()
                .forEach(
                        (key, value) -> {
                            if (value == null || value.length != 1 || value[0].length() > 200)
                                throw new FinanceException("INVALID_FILTER", 400);
                            result.put(key, value[0]);
                        });
        return result;
    }

    public static Object[] organizationRoute(String path) {
        if (path == null) throw new FinanceException("NOT_FOUND", 404);
        String[] parts = path.split("/", -1);
        if (parts.length != 3 || !(parts[2].equals("reports") || parts[2].equals("overview")))
            throw new FinanceException("NOT_FOUND", 404);
        UUID org = ReportFilter.uuid(parts[1]);
        if (org == null) throw new FinanceException("NOT_FOUND", 404);
        return new Object[] {org, parts[2]};
    }

    public static UserPrincipal actor(HttpServletRequest request) {
        return SecurityContext.getCurrentUser(request)
                .orElseThrow(() -> new FinanceException("UNAUTHENTICATED", 401));
    }

    public static void json(HttpServletResponse response, String json) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getWriter().write(json);
    }

    public static void error(HttpServletResponse response, FinanceException e) throws IOException {
        if (response.isCommitted()) throw new IOException("Read-side response interrupted");
        response.reset();
        response.setStatus(e.status());
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter()
                .write(
                        FinanceJson.write(
                                Map.of(
                                        "error",
                                        Map.of(
                                                "code",
                                                e.code(),
                                                "message",
                                                "Không thể tải dữ liệu theo yêu cầu.",
                                                "correlationId",
                                                UUID.randomUUID()))));
    }

    public static String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
