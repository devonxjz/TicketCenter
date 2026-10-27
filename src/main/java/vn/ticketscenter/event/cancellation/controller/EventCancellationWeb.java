package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.persistence.DatabasePrincipal;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.event.cancellation.dto.EventCancelRequestDto;
import vn.ticketscenter.event.cancellation.cache.CancellationProgressCache;
import vn.ticketscenter.event.cancellation.service.EventCancellationService;
import vn.ticketscenter.event.cancellation.service.EventCancellationServiceImpl;
import vn.ticketscenter.event.dao.EventCancellationRepository;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

final class EventCancellationWeb {
    private static final int MAX_BODY_LENGTH = 8192;

    private EventCancellationWeb() {}

    static UUID id(String value) {
        return ApiRequest.uuid(value).orElseThrow(EventCancellationWeb::invalid);
    }

    static RefundException invalid() {
        return new RefundException(
                400, "CANCELLATION_INVALID", "Thông tin hủy sự kiện không hợp lệ.");
    }

    static UUID target(HttpServletRequest request, String action) {
        String path = ApiRequest.path(request);
        String[] pieces = path.split("/", -1);
        if (pieces.length != 6
                || !pieces[1].equals("api")
                || !pieces[2].equals("admin")
                || !pieces[3].equals("events")
                || !pieces[5].equals(action)) {
            throw new RefundException(404, "ROUTE_NOT_FOUND", "Đường dẫn không tồn tại.");
        }
        return id(pieces[4]);
    }

    static UserPrincipal actor(HttpServletRequest request) {
        var actor =
                SecurityContext.getCurrentUser(request)
                        .orElseThrow(
                                () ->
                                        new RefundException(
                                                401, "UNAUTHENTICATED", "Bạn cần đăng nhập."));
        if (!actor.isAdmin())
            throw new RefundException(403, "ADMIN_REQUIRED", "Chỉ quản trị viên được hủy sự kiện.");
        return actor; // SQL verifies the current status, authVersion and role.
    }

    static UUID mutation(HttpServletRequest request) {
        if (!SecurityContext.validateCsrfToken(request)) {
            throw new RefundException(403, "CSRF_INVALID", "Mã bảo vệ phiên không hợp lệ.");
        }
        if (!ApiRequest.isJson(request)) {
            throw new RefundException(415, "JSON_REQUIRED", "Yêu cầu phải sử dụng JSON.");
        }
        return id(request.getHeader("Idempotency-Key"));
    }

    static EventCancelRequestDto body(HttpServletRequest request) throws IOException {
        return parse(readBody(request));
    }

    static String readBody(HttpServletRequest request) throws IOException {
        var source = new StringBuilder();
        char[] buffer = new char[1024];
        try (var reader = request.getReader()) {
            for (int count; (count = reader.read(buffer)) != -1; ) {
                source.append(buffer, 0, count);
                if (source.length() > MAX_BODY_LENGTH) {
                    throw new RefundException(
                            413, "PAYLOAD_TOO_LARGE", "Nội dung yêu cầu quá lớn.");
                }
            }
        }
        return source.toString();
    }

    static EventCancelRequestDto parse(String body) {
        try {
            // Require a string reason; the shared parser also accepts numeric/null values.
            var fields = JsonObjectParser.parse(new StringReader(body));
            if (fields.size() != 1
                    || !fields.containsKey("reason")
                    || !body.stripLeading().matches("(?s)\\{\\s*\"reason\"\\s*:\\s*\".*"))
                throw invalid();
            return new EventCancelRequestDto(fields.get("reason"));
        } catch (IOException | IllegalArgumentException failure) {
            throw invalid();
        }
    }

    static EventCancellationService service(HttpServletRequest request) {
        var context = request.getServletContext();
        synchronized (context) {
            var existing = context.getAttribute(EventCancellationService.class.getName());
            if (existing instanceof EventCancellationService service) return service;
            var configured = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(configured instanceof PersistenceRegistry registry)
                    || !registry.hasPrincipal(DatabasePrincipal.ADMIN)) {
                throw new RefundException(
                        503, "DATABASE_UNAVAILABLE", "Hệ thống hủy sự kiện chưa sẵn sàng.");
            }
            var service =
                    new EventCancellationServiceImpl(
                            new EventCancellationRepository(registry.transactionManager()),
                            new CancellationProgressCache());
            context.setAttribute(EventCancellationService.class.getName(), service);
            return service;
        }
    }

    static void error(HttpServletResponse response, RuntimeException failure) throws IOException {
        if (failure instanceof RefundException error) {
            HttpResponses.error(response, error);
        } else {
            HttpResponses.error(
                    response,
                    503,
                    "CANCELLATION_UNAVAILABLE",
                    "Hệ thống hủy sự kiện tạm thời không khả dụng.");
        }
    }
}
