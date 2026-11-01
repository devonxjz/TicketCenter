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
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.service.OtpService;

import java.io.IOException;

@WebServlet(
        name = "OtpVerifyServlet",
        urlPatterns = {"/api/auth/otp/verify", "/auth/verify-email"})
public class OtpVerifyServlet extends HttpServlet {

    private OtpService otpService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/auth/verify-email.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        OtpService service = otpService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }
        try {
            RequestFields fields = RequestFields.read(request);
            String email = fields.get("email");
            String code = fields.get("code");
            if (email == null || email.isBlank() || code == null || code.isBlank()) {
                throw new IdentityException(
                        IdentityError.VALIDATION_FAILED, "Email và mã OTP không được để trống");
            }
            OtpPurpose purpose = OtpSendServlet.parsePurpose(fields.get("purpose"));
            HttpResponses.data(response, service.verifyOtp(requestOf(email, purpose, code)).toJson());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(response, failure, "Đã xảy ra lỗi khi xác minh mã OTP");
        }
    }

    /** A malformed code is reported as a wrong OTP, like every other verification failure. */
    private static OtpVerifyRequestDto requestOf(String email, OtpPurpose purpose, String code) {
        try {
            return new OtpVerifyRequestDto(email, purpose, code);
        } catch (IllegalArgumentException invalid) {
            throw new IdentityException(IdentityError.OTP_INVALID, invalid.getMessage(), invalid);
        }
    }
}
