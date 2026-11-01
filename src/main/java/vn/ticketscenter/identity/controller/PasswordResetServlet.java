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
import vn.ticketscenter.identity.dto.PasswordResetRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.service.PasswordResetService;

import java.io.IOException;

@WebServlet(
        name = "PasswordResetServlet",
        urlPatterns = {"/api/auth/password/reset", "/auth/password/reset", "/auth/reset-password"})
public class PasswordResetServlet extends HttpServlet {

    private PasswordResetService passwordResetService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.passwordResetService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = request.getParameter("token");
        if (token != null && !token.isBlank() && token.matches("^[A-Za-z0-9_.-]+$")) {
            request.setAttribute("token", token);
        }
        request.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/auth/reset-password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PasswordResetService service = passwordResetService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }
        try {
            RequestFields fields = RequestFields.read(request);
            String token = fields.get("token");
            String newPassword = fields.get("newPassword");
            if (token == null || token.isBlank() || newPassword == null || newPassword.isBlank()) {
                throw new IdentityException(
                        IdentityError.VALIDATION_FAILED,
                        "Token và mật khẩu mới không được để trống");
            }
            service.resetPassword(new PasswordResetRequestDto(token, newPassword));
            HttpResponses.data(response, JsonObjectBuilder.object().put("reset", true).build());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(response, failure, "Đã xảy ra lỗi khi đặt lại mật khẩu");
        }
    }
}
