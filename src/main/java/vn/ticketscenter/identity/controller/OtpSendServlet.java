package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.OtpSendRequestDto;
import vn.ticketscenter.identity.model.OtpPurpose;
import vn.ticketscenter.identity.service.OtpService;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "OtpSendServlet", urlPatterns = {"/api/auth/otp/send"})
public class OtpSendServlet extends HttpServlet {

    private OtpService otpService;

    public OtpSendServlet() {}

    public OtpSendServlet(OtpService otpService) {
        this.otpService = otpService;
    }

    private OtpService otpService(ServletContext context) {
        if (otpService != null) return otpService;
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        OtpService service = otpService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        String email = null;
        String purposeStr = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                email = json.get("email");
                purposeStr = json.get("purpose");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            email = req.getParameter("email");
            purposeStr = req.getParameter("purpose");
        }

        if (email == null || email.isBlank()) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", "Email không được để trống");
            return;
        }

        OtpPurpose purpose;
        try {
            purpose = (purposeStr != null) ? OtpPurpose.valueOf(purposeStr.trim().toUpperCase()) : OtpPurpose.VERIFY_EMAIL;
        } catch (IllegalArgumentException e) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", "Mục đích OTP không hợp lệ");
            return;
        }

        try {
            service.sendOtp(new OtpSendRequestDto(email, purpose));
            HttpResponses.data(resp, "{\"sent\":true}");
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            if (msg != null && msg.contains("COOLDOWN_ACTIVE")) {
                HttpResponses.error(resp, 429, "COOLDOWN_ACTIVE", msg);
            } else if (msg != null && msg.contains("EMAIL_SEND_FAILED")) {
                HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "EMAIL_SEND_FAILED", msg);
            } else {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "OTP_ERROR", msg);
            }
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "VALIDATION_FAILED", ex.getMessage());
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi khi gửi mã OTP");
        }
    }
}
