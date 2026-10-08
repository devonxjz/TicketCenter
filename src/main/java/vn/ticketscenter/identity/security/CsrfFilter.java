package vn.ticketscenter.identity.security;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;

import java.io.IOException;
import java.util.Set;

/**
 * Bộ lọc bảo vệ tấn công CSRF cho các yêu cầu thay đổi dữ liệu (mutation).
 * Bắt buộc gửi token hợp lệ qua header `X-CSRF-Token` hoặc form parameter `_csrf`.
 */
@WebFilter(urlPatterns = "/api/*")
public final class CsrfFilter implements Filter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();

        if (MUTATING_METHODS.contains(method)) {
            String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

            // Cho phép ngoại lệ đối với IPN Webhook từ cổng thanh toán có chữ ký riêng
            if (path.equals("/api/payments/vnpay/ipn")) {
                chain.doFilter(request, response);
                return;
            }

            // Đăng xuất có tính idempotent: Nếu phiên đã bị hủy trước đó thì cho phép đi tiếp để trả về 200 OK
            if (path.equals("/api/auth/logout") && httpRequest.getSession(false) == null) {
                chain.doFilter(request, response);
                return;
            }

            if (!SecurityContext.validateCsrfToken(httpRequest)) {
                HttpResponses.error(httpResponse, HttpServletResponse.SC_FORBIDDEN,
                        "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
