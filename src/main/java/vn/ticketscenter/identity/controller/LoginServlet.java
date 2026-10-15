package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.JsonObjectBuilder;
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.security.LoginThrottle;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.LoginService;

import java.io.IOException;
import java.net.URI;

@WebServlet(name = "LoginServlet", urlPatterns = {"/api/auth/login", "/auth/login"})
public class LoginServlet extends HttpServlet {

    private final LoginThrottle throttle = new LoginThrottle();

    private LoginService loginService;

    public LoginServlet() {
    }

    public LoginServlet(LoginService loginService) {
        this.loginService = loginService;
    }

    private LoginService loginService(ServletContext context) {
        if (loginService != null) {
            return loginService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.loginService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("showDemoAccounts", ServerConfig.isDemoData());
        req.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        LoginService service = loginService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }

        String identifier;
        String password;
        try {
            RequestFields fields = RequestFields.read(req);
            identifier = fields.first("identifier", "email", "userName", "username");
            password = fields.get("password");
        } catch (IdentityException invalidJson) {
            IdentityErrorWriter.write(resp, invalidJson, null);
            return;
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
            UserPrincipal principal = service.login(new LoginRequestDto(identifier, password));
            throttle.recordSuccess(identifier, client);

            // Chống Session Fixation: Cấp mới session ID, lưu UserPrincipal và rotate CSRF token
            SecurityContext.setSessionUser(req, principal);

            // Cấp mới CSRF token gắn session và trả về qua response header
            resp.setHeader("X-CSRF-Token", SecurityContext.getOrCreateCsrfToken(req));

            String targetRedirect = redirectTarget(req, principal);
            boolean isApiRequest = req.getRequestURI().contains("/api/") || RequestFields.isJson(req);
            if (isApiRequest) {
                HttpResponses.data(resp, JsonObjectBuilder.object()
                        .put("authenticated", true)
                        .put("role", roleName(principal))
                        .put("redirect", targetRedirect)
                        .build());
            } else {
                resp.sendRedirect(targetRedirect);
            }
        } catch (IdentityException rejected) {
            throttle.recordFailure(identifier, client);
            IdentityErrorWriter.write(resp, rejected, null);
        } catch (RuntimeException failure) {
            IdentityErrorWriter.serverError(resp, failure, "Đã xảy ra lỗi trong quá trình xử lý đăng nhập");
        }
    }

    private static String roleName(UserPrincipal principal) {
        if (principal.isAdmin()) {
            return "ADMIN";
        }
        if (principal.organizationRole() == OrganizationRole.MANAGER) {
            return "MANAGER";
        }
        if (principal.organizationRole() == OrganizationRole.CHECK_IN_STAFF) {
            return "STAFF";
        }
        return "CUSTOMER";
    }

    /** The requested {@code redirect} when it is local and the role may open it, else the home page. */
    private static String redirectTarget(HttpServletRequest req, UserPrincipal principal) {
        String contextPath = req.getContextPath();
        String requested = req.getParameter("redirect");
        if (isLocalRedirect(requested, contextPath) && isRoleRedirectAllowed(principal, requested, contextPath)) {
            return requested;
        }
        return contextPath + "/";
    }

    /** Apply role checks to the decoded route, excluding query values. */
    static boolean isRoleRedirectAllowed(UserPrincipal principal, String target, String contextPath) {
        String path;
        try {
            path = URI.create(target).getPath();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        String base = contextPath == null ? "" : contextPath;
        if (path == null || !path.startsWith(base)) return false;
        path = path.substring(base.length());
        if (principal.isAdmin()) return true;
        if (path.equals("/admin") || path.startsWith("/admin/")) return false;
        if (path.equals("/organizer/request")) return true;
        if (path.equals("/organizer") || path.startsWith("/organizer/")) {
            boolean checkInPage = path.equals("/organizer/checkin") || path.startsWith("/organizer/checkin/");
            return principal.isManager() || (principal.isStaff() && checkInPage);
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
