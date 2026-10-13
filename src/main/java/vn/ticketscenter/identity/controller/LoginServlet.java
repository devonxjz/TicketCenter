package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.security.AuthSecurityListener;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "LoginServlet", urlPatterns = {"/api/auth/login", "/auth/login"})
public class LoginServlet extends HttpServlet {

    private final vn.ticketscenter.identity.security.LoginThrottle throttle = new vn.ticketscenter.identity.security.LoginThrottle();

    private AuthService authService;

    public LoginServlet() {}

    public LoginServlet(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void init(ServletConfig config) {
        if (this.authService == null && config != null) {
            this.authService = AuthSecurityListener.resolveAuthService(config.getServletContext());
        }
    }

    private AuthService getAuthService(ServletContext context) {
        if (authService != null) return authService;
        return AuthSecurityListener.resolveAuthService(context);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("showDemoAccounts", vn.ticketscenter.config.ServerConfig.isDemoData());
        String csrfToken = SecurityContext.getOrCreateCsrfToken(req);
        req.setAttribute("csrfToken", csrfToken);
        req.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AuthService service = getAuthService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String identifier = null;
        String password = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                identifier = json.get("identifier");
                if (identifier == null) {
                    identifier = json.get("email");
                }
                if (identifier == null) {
                    identifier = json.get("userName");
                }
                if (identifier == null) {
                    identifier = json.get("username");
                }
                password = json.get("password");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            identifier = req.getParameter("identifier");
            if (identifier == null) {
                identifier = req.getParameter("email");
            }
            if (identifier == null) {
                identifier = req.getParameter("userName");
            }
            if (identifier == null) {
                identifier = req.getParameter("username");
            }
            password = req.getParameter("password");
        }

        String client = req.getRemoteAddr();
        long retryAfter = throttle.retryAfterSeconds(identifier, client);
        if (retryAfter > 0) {
            resp.setHeader("Retry-After", Long.toString(retryAfter));
            HttpResponses.error(resp, 429, "LOGIN_THROTTLED",
                    "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau.");
            return;
        }

        try {
            LoginRequestDto dto = new LoginRequestDto(identifier, password);
            UserPrincipal principal = service.login(dto);
            throttle.recordSuccess(identifier, client);

            // Chống Session Fixation: Cấp mới session ID, lưu UserPrincipal và rotate CSRF token
            SecurityContext.setSessionUser(req, principal);

            // Cấp mới CSRF token gắn session và trả về qua response header
            String csrfToken = SecurityContext.getOrCreateCsrfToken(req);
            resp.setHeader("X-CSRF-Token", csrfToken);

            String roleName;
            String defaultRedirect;
            if (principal.isAdmin()) {
                roleName = "ADMIN";
                defaultRedirect = req.getContextPath() + "/";
            } else if (principal.organizationRole() == vn.ticketscenter.identity.model.OrganizationRole.MANAGER) {
                roleName = "MANAGER";
                defaultRedirect = req.getContextPath() + "/";
            } else if (principal.organizationRole() == vn.ticketscenter.identity.model.OrganizationRole.CHECK_IN_STAFF) {
                roleName = "STAFF";
                defaultRedirect = req.getContextPath() + "/";
            } else {
                roleName = "CUSTOMER";
                defaultRedirect = req.getContextPath() + "/";
            }

            String requestedRedirect = req.getParameter("redirect");
            String targetRedirect = defaultRedirect;
            if (isLocalRedirect(requestedRedirect, req.getContextPath())) {
                if (isRoleRedirectAllowed(principal, requestedRedirect, req.getContextPath())) {
                    targetRedirect = requestedRedirect;
                }
            }

            boolean isApiRequest = req.getRequestURI().contains("/api/") || (contentType != null && contentType.startsWith("application/json"));
            if (isApiRequest) {
                HttpResponses.data(resp, "{\"authenticated\":true,\"role\":\"" + roleName + "\",\"redirect\":" + HttpResponses.jsonString(targetRedirect) + "}");
            } else {
                resp.sendRedirect(targetRedirect);
            }
        } catch (SecurityException ex) {
            throttle.recordFailure(identifier, client);
            String code = ex.getMessage();
            String message = "ACCOUNT_DISABLED".equals(code)
                    ? "Tài khoản của bạn đã bị vô hiệu hóa"
                    : "Email hoặc mật khẩu không chính xác";
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED, code, message);
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SERVER_ERROR", "Đã xảy ra lỗi trong quá trình xử lý đăng nhập");
        }
    }

    /** Apply role checks to the decoded route, excluding query values. */
    static boolean isRoleRedirectAllowed(UserPrincipal principal, String target, String contextPath) {
        String path;
        try { path = java.net.URI.create(target).getPath(); }
        catch (IllegalArgumentException invalid) { return false; }
        String base = contextPath == null ? "" : contextPath;
        if (path == null || !path.startsWith(base)) return false;
        path = path.substring(base.length());
        if (principal.isAdmin()) return true;
        if (path.equals("/admin") || path.startsWith("/admin/")) return false;
        if (path.equals("/organizer/request")) return true;
        if (path.equals("/organizer") || path.startsWith("/organizer/")) {
            return principal.isManager() || (principal.isStaff() &&
                    (path.equals("/organizer/checkin") || path.startsWith("/organizer/checkin/")));
        }
        return true;
    }

    /** Only same-application paths are accepted; scheme-relative and backslash forms leave the site. */
    static boolean isLocalRedirect(String target, String contextPath) {
        if (target == null || target.isBlank() || !target.startsWith("/")) return false;
        if (target.startsWith("//") || target.indexOf('\\') >= 0) return false;
        for (int index = 0; index < target.length(); index++) {
            if (Character.isISOControl(target.charAt(index))) return false;
        }
        String base = contextPath == null ? "" : contextPath;
        return base.isEmpty() || target.equals(base) || target.startsWith(base + "/") || target.startsWith(base + "?");
    }
}
