package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.LoginRequestDto;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.PasswordHasher;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "LoginServlet", urlPatterns = {"/api/auth/login", "/auth/login"})
public class LoginServlet extends HttpServlet {

    private AuthService authService;

    public LoginServlet() {}

    public LoginServlet(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void init(ServletConfig config) {
        resolveAuthService(config.getServletContext());
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
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
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
            password = req.getParameter("password");
        }

        try {
            LoginRequestDto dto = new LoginRequestDto(identifier, password);
            UserPrincipal principal = service.login(dto);

            // Chống Session Fixation: Cấp mới session ID, lưu UserPrincipal và rotate CSRF token
            SecurityContext.setSessionUser(req, principal);

            // Cấp mới CSRF token gắn session và trả về qua response header
            String csrfToken = SecurityContext.getOrCreateCsrfToken(req);
            resp.setHeader("X-CSRF-Token", csrfToken);

            HttpResponses.data(resp, "{\"authenticated\":true}");
        } catch (SecurityException ex) {
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
}
