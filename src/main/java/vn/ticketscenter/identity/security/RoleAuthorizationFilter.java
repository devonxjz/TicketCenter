package vn.ticketscenter.identity.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.identity.security.RouteAccessPolicy.Decision;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Enforces {@link RouteAccessPolicy} on the admin and organiser areas; anonymous visitors are sent to the login
 * page and come back to the page they asked for.
 */
@WebFilter(urlPatterns = {"/admin/*", "/organizer/*"})
public final class RoleAuthorizationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Optional<UserPrincipal> user = SecurityContext.getCurrentUser(httpRequest);
        if (user.isEmpty()) {
            redirectToLogin(httpRequest, httpResponse);
            return;
        }

        String path = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());
        Decision decision = RouteAccessPolicy.decide(user.get(), path);
        switch (decision) {
            case ALLOW -> chain.doFilter(request, response);
            case DENY_ADMIN_AREA ->
                    httpResponse.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Bạn không có quyền truy cập vào Trung tâm Quản trị Admin");
            case DENY_ORGANIZER_AREA ->
                    httpResponse.sendError(
                            HttpServletResponse.SC_FORBIDDEN,
                            "Khách hàng mua vé không có quyền truy cập kênh ban tổ chức hoặc soát vé");
            case REDIRECT_TO_CHECK_IN ->
                    httpResponse.sendRedirect(
                            httpRequest.getContextPath() + RouteAccessPolicy.CHECK_IN_HOME);
        }
    }

    private static void redirectToLogin(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String target = request.getRequestURI();
        if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
            target += "?" + request.getQueryString();
        }
        response.sendRedirect(
                request.getContextPath()
                        + "/auth/login?redirect="
                        + URLEncoder.encode(target, StandardCharsets.UTF_8));
    }
}
