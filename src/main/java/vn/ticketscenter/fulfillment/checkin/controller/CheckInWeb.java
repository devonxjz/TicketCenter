package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.security.SecurityContext;
import java.io.IOException;
import java.util.UUID;

final class CheckInWeb {
    private CheckInWeb() {}

    static UUID actor(HttpServletRequest request) {
        var principal =
                SecurityContext.getCurrentUser(request)
                        .orElseThrow(
                                () ->
                                        new CheckInException(
                                                401,
                                                "UNAUTHENTICATED",
                                                "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn."));
        // Named forwarding may precede the shared REQUEST filter; HTML pages also need session
        // validation.
        var auth = IdentityServices.sessionValidator(request.getServletContext());
        if (auth == null)
            throw new CheckInException(503, "AUTH_UNAVAILABLE", "Hệ thống xác thực chưa sẵn sàng.");
        if (!auth.isSessionValid(principal.actorId(), principal.authVersion())) {
            SecurityContext.clearSession(request);
            throw new CheckInException(
                    401, "SESSION_EXPIRED", "Phiên làm việc đã hết hạn hoặc tài khoản đã bị khóa.");
        }
        return principal.actorId();
    }

    static UUID id(String value) {
        if (value == null
                || !value.matches(
                        "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new CheckInException(400, "INVALID_ID", "Mã định danh không hợp lệ.");
        }
        return UUID.fromString(value);
    }

    static void error(HttpServletResponse response, RuntimeException exception) throws IOException {
        if (exception instanceof CheckInException e)
            HttpResponses.error(response, e.httpStatus(), e.code(), e.getMessage());
        else
            HttpResponses.error(
                    response,
                    503,
                    "DATABASE_UNAVAILABLE",
                    "Hệ thống soát vé tạm thời không khả dụng.");
    }
}
