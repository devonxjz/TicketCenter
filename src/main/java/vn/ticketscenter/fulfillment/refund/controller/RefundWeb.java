package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.web.*;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.security.*;

final class RefundWeb {
    static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private RefundWeb() {}

    static UserPrincipal actor(HttpServletRequest request) {
        var actor =
                SecurityContext.getCurrentUser(request)
                        .orElseThrow(
                                () ->
                                        new RefundException(
                                                401, "UNAUTHENTICATED", "Bạn cần đăng nhập."));
        var auth = IdentityServices.sessionValidator(request.getServletContext());
        if (auth == null)
            throw new RefundException(503, "AUTH_UNAVAILABLE", "Hệ thống xác thực chưa sẵn sàng.");
        if (!auth.isSessionValid(actor.actorId(), actor.authVersion())) {
            SecurityContext.clearSession(request);
            throw new RefundException(401, "SESSION_EXPIRED", "Phiên đăng nhập đã hết hạn.");
        }
        return actor;
    }

    static UUID id(String value) {
        if (value == null || !value.matches(UUID_PATTERN)) throw RefundException.invalid();
        return UUID.fromString(value);
    }

    static int page(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw RefundException.invalid();
        }
    }

    static UUID mutation(HttpServletRequest request) {
        if (!SecurityContext.validateCsrfToken(request))
            throw new RefundException(403, "CSRF_INVALID", "Mã bảo vệ phiên không hợp lệ.");
        if (request.getContentType() == null
                || !request.getContentType()
                        .split(";", 2)[0]
                        .strip()
                        .equalsIgnoreCase("application/json"))
            throw new RefundException(415, "JSON_REQUIRED", "Yêu cầu phải sử dụng JSON.");
        return id(request.getHeader("Idempotency-Key"));
    }

    static CreateRefundRequestDto create(HttpServletRequest request) throws IOException {
        StringBuilder body = new StringBuilder();
        char[] buffer = new char[1024];
        int n;
        try (var reader = request.getReader()) {
            while ((n = reader.read(buffer)) != -1) {
                body.append(buffer, 0, n);
                if (body.length() > 32768) throw RefundException.invalid();
            }
        }
        return RefundRequestJson.parse(body.toString());
    }

    /** SQL error tables raise plain {@link AppException}, so match the base type, not RefundException. */
    static void error(HttpServletResponse response, RuntimeException failure) throws IOException {
        if (failure instanceof AppException known) {
            HttpResponses.error(response, known.status(), known.code(), known.getMessage());
            return;
        }
        HttpResponses.error(
                response, 503, "REFUND_UNAVAILABLE", "Hệ thống hoàn vé tạm thời không khả dụng.");
    }

    static String path(HttpServletRequest r) {
        return r.getRequestURI().substring(r.getContextPath().length());
    }
}
