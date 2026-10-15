package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.JsonObjectBuilder;
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.dto.RegisterRequestDto;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.service.OtpService;
import vn.ticketscenter.identity.service.RegistrationService;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

@WebServlet(name = "RegisterServlet", urlPatterns = {"/api/auth/register", "/auth/register"})
public class RegisterServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(RegisterServlet.class.getName());

    private RegistrationService registrationService;
    private OtpService otpService;

    public RegisterServlet() {
    }

    public RegisterServlet(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    public RegisterServlet(RegistrationService registrationService, OtpService otpService) {
        this.registrationService = registrationService;
        this.otpService = otpService;
    }

    private RegistrationService registrationService(ServletContext context) {
        if (registrationService != null) {
            return registrationService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.registrationService();
    }

    private OtpService otpService(ServletContext context) {
        if (otpService != null) {
            return otpService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    /**
     * Gửi mã OTP kích hoạt ngay sau khi đăng ký. Tài khoản đã được tạo nên lỗi gửi mail
     * không làm hỏng phản hồi đăng ký; client dùng cờ otpSent để mời người dùng gửi lại.
     */
    private boolean sendVerificationOtp(ServletContext context, String email) {
        try {
            OtpService service = otpService(context);
            return service != null && service.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Không thể gửi OTP kích hoạt sau khi đăng ký: " + ex.getMessage());
            return false;
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/register.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        RegistrationService service = registrationService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }
        try {
            RequestFields fields = RequestFields.read(req);
            String email = fields.get("email");
            service.register(new RegisterRequestDto(
                    fields.get("userName"), fields.get("fullName"), email, fields.get("password")));
            boolean otpSent = sendVerificationOtp(req.getServletContext(), email);
            HttpResponses.data(resp, JsonObjectBuilder.object()
                    .put("registered", true)
                    .put("otpSent", otpSent)
                    .build());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(resp, failure, "Đã xảy ra lỗi trong quá trình xử lý đăng ký");
        }
    }
}
