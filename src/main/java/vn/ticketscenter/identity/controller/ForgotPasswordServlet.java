package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
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

    private PasswordResetService passwordResetService(ServletContext context) {
        if (passwordResetService != null) return passwordResetService;
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.passwordResetService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("csrfToken", vn.ticketscenter.identity.security.SecurityContext.getOrCreateCsrfToken(req));
        req.getRequestDispatcher("/WEB-INF/views/auth/forgot-password.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        PasswordResetService service = passwordResetService(req.getServletContext());
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
