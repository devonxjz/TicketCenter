package vn.ticketscenter.fulfillment.refund.controller;

import vn.ticketscenter.fulfillment.refund.service.AdminRefundRuntime;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundRetryRequestDto;
import vn.ticketscenter.fulfillment.refund.service.AdminRefundDecisionService;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

@WebServlet(
        name = "AdminRefundRequestsServlet",
        urlPatterns = {
            "/api/admin/refund-requests",
            "/api/admin/refund-requests/*",
            "/admin/refund-requests",
            "/admin/refund-requests/*"
        })
public final class AdminRefundRequestsServlet extends HttpServlet {
    private static final String API_PREFIX = "/api/admin/refund-requests";
    private static final String ADMIN_PREFIX = "/admin/refund-requests";
    private static final int MAX_PAYLOAD_BYTES = 8192;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            UserPrincipal actor = RefundWeb.requireAdmin(request);
            AdminRefundDecisionService service = AdminRefundRuntime.resolve(request.getServletContext());
            String path = RefundWeb.path(request);
            boolean api = path.startsWith("/api/");
            String prefix = api ? API_PREFIX : ADMIN_PREFIX;
            response.setHeader("Cache-Control", "no-store");

            if (path.equals(prefix)) {
                handleList(request, response, actor, service, api);
            } else {
                handleDetail(request, response, actor, service, api, prefix, path);
            }
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            String path = RefundWeb.path(request);
            AdminRefundDecisionService service = AdminRefundRuntime.resolve(request.getServletContext());

            if (path.endsWith("/decision")) {
                handleDecision(request, response, service);
            } else if (path.endsWith("/retry")) {
                handleRetry(request, response, service);
            } else {
                throw new RefundException(
                        405, "METHOD_NOT_ALLOWED", "Đường dẫn này không nhận thao tác ghi.");
            }
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }

    private static void handleList(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal actor,
            AdminRefundDecisionService service,
            boolean api)
            throws IOException, ServletException {
        int page = RefundWeb.page(request.getParameter("page"), 1);
        int size = RefundWeb.page(request.getParameter("pageSize"), 20);
        var result = service.list(actor, request.getParameter("status"), page, size);
        if (api) {
            HttpResponses.data(response, result.json());
            return;
        }
        request.setAttribute("refundPageJson", result.json());
        request.setAttribute("statusFilter", request.getParameter("status"));
        request.getRequestDispatcher("/WEB-INF/views/admin/refunds/list.jsp")
                .forward(request, response);
    }

    private static void handleDetail(
            HttpServletRequest request,
            HttpServletResponse response,
            UserPrincipal actor,
            AdminRefundDecisionService service,
            boolean api,
            String prefix,
            String path)
            throws IOException, ServletException {
        var result = service.detail(actor, RefundWeb.id(path.substring(prefix.length() + 1)));
        if (api) {
            HttpResponses.data(response, result.json());
            return;
        }
        request.setAttribute("refundId", result.refund().refundId().toString());
        request.setAttribute("csrf", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/admin/refunds/review.jsp")
                .forward(request, response);
    }

    private static void handleDecision(
            HttpServletRequest request,
            HttpServletResponse response,
            AdminRefundDecisionService service)
            throws IOException {
        UUID id = extractTargetId(request, "decision");
        UserPrincipal actor = RefundWeb.requireAdmin(request);
        UUID key = RefundWeb.mutation(request);
        var decision = AdminRefundJson.decision(readBody(request));
        var result = service.decide(actor, id, decision, key);
        HttpResponses.data(response, result.json());
    }

    private static void handleRetry(
            HttpServletRequest request,
            HttpServletResponse response,
            AdminRefundDecisionService service)
            throws IOException {
        UUID id = extractTargetId(request, "retry");
        UserPrincipal actor = RefundWeb.requireAdmin(request);
        UUID key = RefundWeb.mutation(request);
        String body = readBody(request);
        AdminRefundJson.retry(body.isBlank() ? "{}" : body);
        var result = service.retry(actor, id, new AdminRefundRetryRequestDto(key));
        HttpResponses.data(response, result.json());
    }

    private static UUID extractTargetId(HttpServletRequest request, String action) {
        String path = RefundWeb.path(request);
        String prefix = API_PREFIX + "/";
        String suffix = "/" + action;
        if (!path.startsWith(prefix) || !path.endsWith(suffix)) {
            throw new RefundException(404, "ROUTE_NOT_FOUND", "Đường dẫn không tồn tại.");
        }
        return RefundWeb.id(path.substring(prefix.length(), path.length() - suffix.length()));
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        var result = new StringBuilder();
        var buffer = new char[1024];
        int count;
        try (var reader = request.getReader()) {
            while ((count = reader.read(buffer)) != -1) {
                result.append(buffer, 0, count);
                if (result.length() > MAX_PAYLOAD_BYTES) {
                    throw new RefundException(
                            413, "PAYLOAD_TOO_LARGE", "Nội dung yêu cầu quá lớn.");
                }
            }
        }
        return result.toString();
    }
}
