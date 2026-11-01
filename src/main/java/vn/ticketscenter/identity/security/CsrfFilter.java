package vn.ticketscenter.identity.security;

import vn.ticketscenter.config.payment.PayosConfig;
import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;

import java.io.IOException;
import java.util.Set;

/**
 * CSRF protection for requests that change data.
 * A valid token must come in the `X-CSRF-Token` header or the `_csrf` form parameter.
 */
@WebFilter(urlPatterns = "/api/*")
public final class CsrfFilter implements Filter {

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final Set<String> PUBLIC_AUTH_APIS =
            Set.of(
                    "/api/auth/login",
                    "/api/auth/register",
                    "/api/auth/otp/send",
                    "/api/auth/otp/verify",
                    "/api/auth/password/forgot",
                    "/api/auth/password/reset");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();

        if (MUTATING_METHODS.contains(method)) {
            String path =
                    ApiRequest.path(httpRequest);

            // the payOS webhook is server-to-server and checked by its own checksum signature
            if (path.equals(PayosConfig.WEBHOOK_PATH)) {
                chain.doFilter(request, response);
                return;
            }

            // public login/OTP endpoints are used before there is a session
            if (PUBLIC_AUTH_APIS.contains(path)) {
                chain.doFilter(request, response);
                return;
            }

            // logout is idempotent: when the session is already gone, let it through so it
            // answers 200 OK
            if (path.equals("/api/auth/logout") && httpRequest.getSession(false) == null) {
                chain.doFilter(request, response);
                return;
            }

            if (!SecurityContext.validateCsrfToken(httpRequest)) {
                HttpResponses.error(
                        httpResponse,
                        HttpServletResponse.SC_FORBIDDEN,
                        "CSRF_TOKEN_INVALID",
                        "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
