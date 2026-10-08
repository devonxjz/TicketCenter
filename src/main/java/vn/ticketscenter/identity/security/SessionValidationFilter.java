package vn.ticketscenter.identity.security;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.io.IOException;
import java.util.Optional;

/**
 * Bộ lọc kiểm tra tính hợp lệ của phiên đăng nhập trên mỗi request:
 * Đối chiếu User còn ACTIVE và authVersion chưa thay đổi (chống sử dụng phiên sau khi reset password).
 */
@WebFilter(urlPatterns = "/api/*")
public final class SessionValidationFilter implements Filter {

    private AuthService authService;

    public SessionValidationFilter() {}

    public SessionValidationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void init(FilterConfig filterConfig) {
        ServletContext context = filterConfig.getServletContext();
        resolveAuthService(context);
    }

    private synchronized AuthService getAuthService(ServletContext context) {
        if (authService != null) return authService;
        resolveAuthService(context);
        return authService;
    }

    private void resolveAuthService(ServletContext context) {
        if (authService == null && context != null) {
            Object regObj = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (regObj instanceof PersistenceRegistry registry && registry.isReady()) {
                this.authService = new AuthService(
                        registry.transactionManager(),
                        new UserRepository(),
                        new PasswordHasher(),
                        RedisClientProvider.getInstance()
                );
            }
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(httpRequest);

        if (principalOpt.isPresent()) {
            UserPrincipal principal = principalOpt.get();
            AuthService service = getAuthService(request.getServletContext());

            if (service != null && !service.isSessionValid(principal.actorId(), principal.authVersion())) {
                SecurityContext.clearSession(httpRequest);
                HttpResponses.error(httpResponse, HttpServletResponse.SC_UNAUTHORIZED,
                        "SESSION_EXPIRED", "Phiên làm việc đã hết hạn hoặc tài khoản đã bị khóa");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
