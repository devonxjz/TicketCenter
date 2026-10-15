package vn.ticketscenter.config.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import vn.ticketscenter.config.error.AppException;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Đích của các <error-page> trong web.xml: mọi sendError(...) và exception chưa bắt đều về đây.
 * Trang HTML nhận giao diện lỗi của hệ thống; request API (hoặc Accept: application/json) vẫn nhận
 * envelope JSON chuẩn. Không bao giờ đưa stack trace hay thông điệp nội bộ ra cho người dùng.
 */
@WebServlet(name = "ErrorPageServlet", urlPatterns = {"/error-handler"})
public class ErrorPageServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(ErrorPageServlet.class.getName());

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        // Truy cập trực tiếp /error-handler (không phải error dispatch) được coi như trang không tồn tại.
        int status = statusAttribute instanceof Integer code ? code : HttpServletResponse.SC_NOT_FOUND;
        Object failure = request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        AppException appFailure = findAppException(failure);
        // Exception chưa bắt do container gán 500; AppException mang đúng mã HTTP của nó.
        if (appFailure != null && statusAttribute instanceof Integer code && code == 500) {
            status = appFailure.status();
        }
        String originalUri = String.valueOf(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        if (failure instanceof Throwable throwable) {
            LOGGER.log(status >= 500 ? Level.SEVERE : Level.WARNING, "Unhandled error for " + originalUri, throwable);
        }

        response.setStatus(status);
        response.setHeader("Cache-Control", "no-store");

        String accept = request.getHeader("Accept");
        boolean api = originalUri.startsWith(request.getContextPath() + "/api/")
                || (accept != null && accept.contains("application/json") && !accept.contains("text/html"));
        if (api) {
            HttpResponses.error(response, status, appFailure != null ? appFailure.code() : code(status),
                    status == HttpServletResponse.SC_NOT_FOUND ? "Không tìm thấy tài nguyên được yêu cầu." : message(status));
            return;
        }

        request.setAttribute("errorStatus", status);
        request.setAttribute("errorMessage", message(status));
        request.setAttribute("errorTitle", title(status));
        request.getRequestDispatcher("/WEB-INF/views/error/" + view(status)).forward(request, response);
    }

    private static AppException findAppException(Object failure) {
        Throwable current = failure instanceof Throwable throwable ? throwable : null;
        for (int depth = 0; current != null && depth < 10; depth++, current = current.getCause()) {
            if (current instanceof AppException app) {
                return app;
            }
        }
        return null;
    }

    private static String view(int status) {
        return switch (status) {
            case 401, 403 -> "forbidden.jsp";
            case 502, 503, 504 -> "bad-gateway.jsp";
            default -> status >= 500 ? "server-error.jsp" : "not-found.jsp";
        };
    }

    private static String code(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHENTICATED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 502 -> "BAD_GATEWAY";
            case 503 -> "SERVICE_UNAVAILABLE";
            default -> status >= 500 ? "SERVER_ERROR" : "REQUEST_FAILED";
        };
    }

    private static String title(int status) {
        return switch (status) {
            case 401 -> "Cần đăng nhập";
            case 403 -> "Không có quyền truy cập";
            case 404 -> "Không tìm thấy trang";
            case 502, 503, 504 -> "Dịch vụ tạm thời gián đoạn";
            default -> status >= 500 ? "Máy chủ đang gặp sự cố" : "Không thể hoàn tất yêu cầu";
        };
    }

    private static String message(int status) {
        return switch (status) {
            case 400 -> "Yêu cầu không hợp lệ.";
            case 401 -> "Bạn cần đăng nhập để tiếp tục.";
            case 403 -> "Bạn không có quyền truy cập trang này.";
            case 404 -> "Trang bạn tìm không tồn tại hoặc đã được chuyển đi.";
            case 405 -> "Thao tác này không được hỗ trợ.";
            case 502, 504 -> "Hệ thống đang không kết nối được với dịch vụ liên quan. Vui lòng thử lại sau ít phút.";
            case 503 -> "Hệ thống đang tạm thời gián đoạn. Vui lòng thử lại sau ít phút.";
            default -> status >= 500
                    ? "Đã xảy ra lỗi ở máy chủ. Vui lòng thử lại sau ít phút."
                    : "Không thể hoàn tất yêu cầu.";
        };
    }
}
