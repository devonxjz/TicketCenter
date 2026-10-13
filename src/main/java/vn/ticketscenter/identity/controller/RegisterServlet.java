package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.integration.mail.SmtpEmailSender;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.repository.OtpRepository;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.AuthSecurityListener;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.OtpCryptoHelper;
import vn.ticketscenter.identity.service.OtpService;

import jakarta.servlet.ServletException;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "RegisterServlet", urlPatterns = {"/api/auth/register", "/auth/register"})
public class RegisterServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(RegisterServlet.class.getName());

    private AuthService authService;
    private OtpService otpService;

    public RegisterServlet() {}

    public RegisterServlet(AuthService authService) {
        this.authService = authService;
    }

    public RegisterServlet(AuthService authService, OtpService otpService) {
        this.authService = authService;
        this.otpService = otpService;
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

    private synchronized OtpService getOtpService(ServletContext context) {
        if (otpService == null && context != null) {
            Object regObj = context.getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (regObj instanceof PersistenceRegistry registry && registry.isReady()) {
                this.otpService = new OtpService(
                        registry.transactionManager(),
                        new OtpRepository(),
                        new UserRepository(),
                        new OtpCryptoHelper(),
                        new SmtpEmailSender()
                );
            }
        }
        return otpService;
    }

    /**
     * Gửi mã OTP kích hoạt ngay sau khi đăng ký. Tài khoản đã được tạo nên lỗi gửi mail
     * không làm hỏng phản hồi đăng ký; client dùng cờ otpSent để mời người dùng gửi lại.
     */
    private boolean sendVerificationOtp(ServletContext context, String email) {
        try {
            OtpService service = getOtpService(context);
            return service != null && service.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Không thể gửi OTP kích hoạt sau khi đăng ký: " + ex.getMessage());
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String csrfToken = SecurityContext.getOrCreateCsrfToken(req);
        req.setAttribute("csrfToken", csrfToken);
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
            boolean otpSent = sendVerificationOtp(req.getServletContext(), email);
            HttpResponses.data(resp, "{\"registered\":true,\"otpSent\":" + otpSent + "}");
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
