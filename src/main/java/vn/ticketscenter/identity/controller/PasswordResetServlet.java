package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.redis.RedisClientProvider;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.OtpCryptoHelper;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "PasswordResetServlet", urlPatterns = {"/api/auth/password/reset", "/auth/password/reset", "/auth/reset-password"})
public class PasswordResetServlet extends HttpServlet {

    private PasswordResetService passwordResetService;

    public PasswordResetServlet() {}

    public PasswordResetServlet(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @Override
    public void init(ServletConfig config) {
        resolveService(config.getServletContext());
    }

    private synchronized PasswordResetService getPasswordResetService(ServletContext context) {
        if (passwordResetService != null) return passwordResetService;
        resolveService(context);
        return passwordResetService;
    }

    private void resolveService(ServletContext context) {
        if (passwordResetService == null && context != null) {
            Object regObj = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (regObj instanceof PersistenceRegistry registry && registry.isReady()) {
                OtpCryptoHelper cryptoHelper = new OtpCryptoHelper();
                OtpRepository otpRepository = new OtpRepository();
                UserRepository userRepository = new UserRepository();
                OtpService otpService = new OtpService(
                        registry.transactionManager(),
                        otpRepository,
                        userRepository,
                        cryptoHelper,
                        new SmtpEmailSender()
                );
                this.passwordResetService = new PasswordResetService(
                        registry.transactionManager(),
                        userRepository,
                        otpService,
                        cryptoHelper,
                        new PasswordHasher(),
                        RedisClientProvider.getInstance()
                );
            }
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String token = req.getParameter("token");
        if (token != null && !token.isBlank() && token.matches("^[A-Za-z0-9_.-]+$")) {
            req.setAttribute("token", token);
        }
        req.setAttribute("csrfToken", vn.ticketscenter.identity.security.SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/reset-password.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PasswordResetService service = getPasswordResetService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String token = null;
        String newPassword = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                token = json.get("token");
                newPassword = json.get("newPassword");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            token = req.getParameter("token");
            newPassword = req.getParameter("newPassword");
        }

        if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", "Token và mật khẩu mới không được để trống");
            return;
        }

        try {
            service.resetPassword(new PasswordResetRequestDto(token, newPassword));
            HttpResponses.data(resp, "{\"reset\":true}");
        } catch (SecurityException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_TOKEN", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi khi đặt lại mật khẩu");
        }
    }
}
