package vn.ticketscenter.identity.security;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.identity.model.OrganizationRole;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Filter kiểm soát phân quyền truy cập trang quản trị và vận hành:
 * - Admin: Toàn quyền truy cập /admin/* và /organizer/*
 * - Manager: Toàn quyền /organizer/*, KHÔNG vào được /admin/*
 * - Check-in Staff: Chỉ được vào /organizer/checkin* (/checkin-select, /checkin-scanner).
 *   KHÔNG vào được /admin/* và các trang quản lý khác của ban tổ chức.
 * - Customer: KHÔNG vào được /admin/* và /organizer/* (kể cả checkin).
 */
@WebFilter(urlPatterns = {"/admin/*", "/organizer/*"})
public final class RoleAuthorizationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

        Optional<UserPrincipal> userOpt = SecurityContext.getCurrentUser(httpRequest);

        // 1. Chưa đăng nhập -> Chuyển hướng sang /auth/login với param redirect
        if (userOpt.isEmpty()) {
            String targetUri = httpRequest.getRequestURI();
            if (httpRequest.getQueryString() != null && !httpRequest.getQueryString().isBlank()) {
                targetUri += "?" + httpRequest.getQueryString();
            }
            httpResponse.sendRedirect(httpRequest.getContextPath() + "/auth/login?redirect="
                    + URLEncoder.encode(targetUri, StandardCharsets.UTF_8));
            return;
        }

        UserPrincipal user = userOpt.get();

        // 2. Kiểm tra truy cập khu vực /admin/*
        if (path.startsWith("/admin")) {
            if (!user.isAdmin()) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Bạn không có quyền truy cập vào Trung tâm Quản trị Admin");
                return;
            }
        }

        // 3. Kiểm tra truy cập khu vực /organizer/*
        if (path.startsWith("/organizer")) {
            if (user.isAdmin()) {
                chain.doFilter(request, response);
                return;
            }

            boolean isCheckinRoute = path.startsWith("/organizer/checkin");

            // Customer thông thường -> Không có quyền vào bất kỳ trang nào của /organizer
            if (user.isCustomerOnly()) {
                httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
                        "Khách hàng mua vé không có quyền truy cập kênh ban tổ chức hoặc soát vé");
                return;
            }

            // Nhân viên soát vé (CHECK_IN_STAFF)
            if (user.organizationRole() == OrganizationRole.CHECK_IN_STAFF) {
                if (!isCheckinRoute) {
                    httpResponse.sendRedirect(httpRequest.getContextPath() + "/organizer/checkin-select");
                    return;
                }
            }
        }

        chain.doFilter(request, response);
    }
}
