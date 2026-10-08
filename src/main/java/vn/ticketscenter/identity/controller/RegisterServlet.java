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
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.PasswordHasher;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "RegisterServlet", urlPatterns = {"/api/auth/register", "/auth/register"})
public class RegisterServlet extends HttpServlet {

    private AuthService authService;

    public RegisterServlet() {}

    public RegisterServlet(AuthService authService) {
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
        req.getRequestDispatcher("/WEB-INF/views/auth/register.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AuthService service = getAuthService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String userName = null;
        String fullName = null;
        String email = null;
        String password = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                userName = json.get("userName");
                fullName = json.get("fullName");
                email = json.get("email");
                password = json.get("password");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            userName = req.getParameter("userName");
            fullName = req.getParameter("fullName");
            email = req.getParameter("email");
            password = req.getParameter("password");
        }

        try {
            RegisterRequestDto dto = new RegisterRequestDto(userName, fullName, email, password);
            service.register(dto);
            HttpResponses.data(resp, "{\"registered\":true}");
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", ex.getMessage());
        } catch (IllegalStateException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT,
                    "IDENTITY_CONFLICT", ex.getMessage());
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SERVER_ERROR", "Đã xảy ra lỗi trong quá trình xử lý đăng ký");
        }
    }
}
