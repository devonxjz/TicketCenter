package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.ServerConfig;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.JsonObjectBuilder;
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.security.LoginThrottle;
import vn.ticketscenter.identity.security.RouteAccessPolicy;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.LoginService;

import java.io.IOException;
import java.net.URI;

@WebServlet(
        name = "LoginServlet",
        urlPatterns = {"/api/auth/login", "/auth/login"})
public class LoginServlet extends HttpServlet {

    private LoginService loginService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.loginService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("showDemoAccounts", ServerConfig.isDemoData());
        request.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/auth/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        LoginService service = loginService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }

        String identifier;
        String password;

        try {
            RequestFields fields = RequestFields.read(request);
            identifier = fields.first("identifier", "email", "userName", "username");
            password = fields.get("password");
        } catch (IdentityException invalidJson) {
            IdentityErrorWriter.write(response, invalidJson, null);
            return;
        }

        LoginThrottle throttle = LoginThrottle.from(getServletContext());
        String client = request.getRemoteAddr();
        long retryAfter = throttle.retryAfterSeconds(identifier, client);
        if (retryAfter > 0) {
            response.setHeader("Retry-After", Long.toString(retryAfter));
            HttpResponses.error(
                    response,
                    429,
                    "LOGIN_THROTTLED",
                    "Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau.");
            return;
        }

        try {
            UserPrincipal principal = service.login(new LoginRequestDto(identifier, password));
            throttle.recordSuccess(identifier, client);

            // Chống Session Fixation: Cấp mới session ID, lưu UserPrincipal và rotate CSRF token
            SecurityContext.setSessionUser(request, principal);

            // Cấp mới CSRF token gắn session và trả về qua response header
            response.setHeader("X-CSRF-Token", SecurityContext.getOrCreateCsrfToken(request));

            String targetRedirect = redirectTarget(request, principal);
            boolean isApiRequest =
                    request.getRequestURI().contains("/api/") || RequestFields.isJson(request);
            if (isApiRequest) {
                HttpResponses.data(
                        response,
                        JsonObjectBuilder.object()
                                .put("authenticated", true)
                                .put("role", roleName(principal))
                                .put("redirect", targetRedirect)
                                .build());
            } else {
                response.sendRedirect(targetRedirect);
            }
        } catch (IdentityException rejected) {
            throttle.recordFailure(identifier, client);
            IdentityErrorWriter.write(response, rejected, null);
        } catch (RuntimeException failure) {
            IdentityErrorWriter.serverError(
                    response, failure, "Đã xảy ra lỗi trong quá trình xử lý đăng nhập");
        }
    }

    private static String roleName(UserPrincipal principal) {
        if (principal.isAdmin()) return "ADMIN";
        OrganizationRole role = principal.organizationRole();
        if (role == OrganizationRole.MANAGER) return "MANAGER";
        if (role == OrganizationRole.CHECK_IN_STAFF) return "STAFF";
        return "CUSTOMER";
    }

    /** The requested {@code redirect} when it is local and the role may open it, else the home page. */
    private static String redirectTarget(HttpServletRequest request, UserPrincipal principal) {
        String contextPath = request.getContextPath();
        String requested = request.getParameter("redirect");
        if (isLocalRedirect(requested, contextPath)
                && isRoleRedirectAllowed(principal, requested, contextPath)) {
            return requested;
        }
        return contextPath + "/";
    }

    /** Whether {@link RouteAccessPolicy} lets the user open the decoded route, excluding query values. */
    static boolean isRoleRedirectAllowed(
            UserPrincipal principal, String target, String contextPath) {
        String path;
        try {
            path = URI.create(target).getPath();
        } catch (IllegalArgumentException invalid) {
            return false;
        }
        String base = contextPath == null ? "" : contextPath;
        if (path == null || !path.startsWith(base)) {
            return false;
        }
        return RouteAccessPolicy.canOpen(principal, path.substring(base.length()));
    }

    /** Only same-application paths are accepted; scheme-relative and backslash forms leave the site. */
    static boolean isLocalRedirect(String target, String contextPath) {
        if (target == null || target.isBlank() || !target.startsWith("/")) return false;
        if (target.startsWith("//") || target.indexOf('\\') >= 0) return false;
        for (int index = 0; index < target.length(); index++) {
            if (Character.isISOControl(target.charAt(index))) return false;
        }
        String base = contextPath == null ? "" : contextPath;
        return base.isEmpty()
                || target.equals(base)
                || target.startsWith(base + "/")
                || target.startsWith(base + "?");
    }
}
