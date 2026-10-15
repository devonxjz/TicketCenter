package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.OtpVerifyRequestDto;
import vn.ticketscenter.identity.dto.OtpVerifyResponseDto;
import vn.ticketscenter.identity.model.OtpPurpose;
import jakarta.servlet.ServletException;
import vn.ticketscenter.identity.service.OtpService;

import java.io.IOException;
import java.util.Map;

@WebServlet(name = "OtpVerifyServlet", urlPatterns = {"/api/auth/otp/verify", "/auth/verify-email"})
public class OtpVerifyServlet extends HttpServlet {

    private OtpService otpService;

    public OtpVerifyServlet() {}

    public OtpVerifyServlet(OtpService otpService) {
        this.otpService = otpService;
    }

    private OtpService otpService(ServletContext context) {
        if (otpService != null) return otpService;
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.otpService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("csrfToken", vn.ticketscenter.identity.security.SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/verify-email.jsp").forward(req, resp);
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
        String code = null;

        String contentType = req.getContentType();
        if (contentType != null && contentType.startsWith("application/json")) {
            try {
                Map<String, String> json = JsonObjectParser.parse(req.getReader());
                email = json.get("email");
                purposeStr = json.get("purpose");
                code = json.get("code");
            } catch (Exception e) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_JSON", "Định dạng JSON không hợp lệ");
                return;
            }
        } else {
            email = req.getParameter("email");
            purposeStr = req.getParameter("purpose");
            code = req.getParameter("code");
        }

        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST,
                    "VALIDATION_FAILED", "Email và mã OTP không được để trống");
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
            OtpVerifyResponseDto result = service.verifyOtp(new OtpVerifyRequestDto(email, purpose, code));
            if (result.resetToken() != null) {
                HttpResponses.data(resp, "{\"success\":true,\"message\":\"" + result.message() + "\",\"resetToken\":\"" + result.resetToken() + "\"}");
            } else {
                HttpResponses.data(resp, "{\"success\":true,\"message\":\"" + result.message() + "\"}");
            }
        } catch (IllegalStateException ex) {
            String msg = ex.getMessage();
            String codeStr = "OTP_INVALID";
            if (msg != null && msg.contains("OTP_MAX_ATTEMPTS_EXCEEDED")) {
                codeStr = "OTP_MAX_ATTEMPTS_EXCEEDED";
            } else if (msg != null && msg.contains("OTP_EXPIRED")) {
                codeStr = "OTP_EXPIRED";
            } else if (msg != null && msg.contains("OTP_ALREADY_USED")) {
                codeStr = "OTP_ALREADY_USED";
            } else if (msg != null && msg.contains("OTP_NOT_FOUND")) {
                codeStr = "OTP_NOT_FOUND";
            }
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, codeStr, msg);
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "OTP_INVALID", ex.getMessage());
        } catch (Exception ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi khi xác minh mã OTP");
        }
    }
}
