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
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.service.OtpCryptoHelper;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.PasswordHasher;
import vn.ticketscenter.identity.service.PasswordResetService;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "ForgotPasswordServlet", urlPatterns = {"/api/auth/password/forgot", "/auth/password/forgot", "/auth/forgot-password"})
public class ForgotPasswordServlet extends HttpServlet {

    private PasswordResetService passwordResetService;

    public ForgotPasswordServlet() {}

    public ForgotPasswordServlet(PasswordResetService passwordResetService) {
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
        req.setAttribute("csrfToken", vn.ticketscenter.identity.security.SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/forgot-password.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PasswordResetService service = getPasswordResetService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String email = null;
        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                email = json.get("email");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            email = req.getParameter("email");
        }

        if (email == null || email.isBlank()) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", "Email không được để trống");
            return;
        }

        try {
            String message = service.forgotPassword(new ForgotPasswordRequestDto(email));
            HttpResponses.data(resp, "{\"message\":\"" + message + "\"}");
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("COOLDOWN_ACTIVE")) {
                HttpResponses.error(resp, 429, "COOLDOWN_ACTIVE", msg);
            } else {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "OTP_ERROR", msg);
            }
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi khi xử lý quên mật khẩu");
        }
    }
}
