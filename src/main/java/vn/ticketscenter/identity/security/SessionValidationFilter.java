package vn.ticketscenter.identity.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.service.SessionValidator;

import java.io.IOException;
import java.util.Optional;

/**
 * Checks the signed-in session on every request:
 * the user is still ACTIVE and authVersion is unchanged (no session survives a password reset).
 */
@WebFilter(urlPatterns = "/api/*")
public final class SessionValidationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(httpRequest);

        if (principalOpt.isPresent()) {
            UserPrincipal principal = principalOpt.get();
            SessionValidator validator = IdentityServices.sessionValidator(request.getServletContext());

            if (validator != null
                    && !validator.isSessionValid(principal.actorId(), principal.authVersion())) {
                SecurityContext.clearSession(httpRequest);
                HttpResponses.error(
                        httpResponse,
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "SESSION_EXPIRED",
                        "Phiên làm việc đã hết hạn hoặc tài khoản đã bị khóa");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
