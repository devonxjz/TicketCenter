package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
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

@WebServlet(
        name = "RegisterServlet",
        urlPatterns = {"/api/auth/register", "/auth/register"})
public class RegisterServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(RegisterServlet.class.getName());

    private RegistrationService registrationService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.registrationService();
    }

    private OtpService otpService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/auth/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        RegistrationService service = registrationService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }
        try {
            RequestFields fields = RequestFields.read(request);
            String email = fields.get("email");
            service.register(
                    new RegisterRequestDto(
                            fields.get("userName"),
                            fields.get("fullName"),
                            email,
                            fields.get("password")));
            boolean otpSent = sendVerificationOtp(request.getServletContext(), email);
            HttpResponses.data(
                    response,
                    JsonObjectBuilder.object()
                            .put("registered", true)
                            .put("otpSent", otpSent)
                            .build());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(response, failure, "Đã xảy ra lỗi trong quá trình xử lý đăng ký");
        }
    }

    /**
     * Sends the activation OTP right after registration. The account already exists, so a mail failure
     * does not fail the reply; the client uses the otpSent flag to offer a resend.
     */
    private boolean sendVerificationOtp(ServletContext context, String email) {
        try {
            OtpService service = otpService(context);
            return service != null
                    && service.sendOtp(new OtpSendRequestDto(email, OtpPurpose.VERIFY_EMAIL));
        } catch (RuntimeException ex) {
            LOGGER.log(
                    Level.WARNING,
                    "Không thể gửi OTP kích hoạt sau khi đăng ký: " + ex.getMessage());
            return false;
        }
    }
}
