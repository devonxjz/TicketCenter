package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
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
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.service.OtpService;

import java.io.IOException;

@WebServlet(
        name = "OtpSendServlet",
        urlPatterns = {"/api/auth/otp/send"})
public class OtpSendServlet extends HttpServlet {

    private OtpService otpService;

    public OtpSendServlet() {}

    public OtpSendServlet(OtpService otpService) {
        this.otpService = otpService;
    }

    private OtpService otpService(ServletContext context) {
        if (otpService != null) {
            return otpService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OtpService service = otpService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }
        try {
            RequestFields fields = RequestFields.read(req);
            String email = fields.get("email");
            if (email == null || email.isBlank()) {
                throw new IdentityException(
                        IdentityError.VALIDATION_FAILED, "Email không được để trống");
            }
            service.sendOtp(new OtpSendRequestDto(email, parsePurpose(fields.get("purpose"))));
            HttpResponses.data(resp, JsonObjectBuilder.object().put("sent", true).build());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(resp, failure, "Đã xảy ra lỗi khi gửi mã OTP");
        }
    }

    /** Package-private so {@link OtpVerifyServlet} applies the same default and error message. */
    static OtpPurpose parsePurpose(String value) {
        if (value == null) {
            return OtpPurpose.VERIFY_EMAIL;
        }
        try {
            return OtpPurpose.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new IdentityException(
                    IdentityError.VALIDATION_FAILED, "Mục đích OTP không hợp lệ", invalid);
        }
    }
}
