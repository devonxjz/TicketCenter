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
import vn.ticketscenter.identity.dto.ForgotPasswordRequestDto;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.service.PasswordResetService;

import java.io.IOException;

@WebServlet(
        name = "ForgotPasswordServlet",
        urlPatterns = {
            "/api/auth/password/forgot",
            "/auth/password/forgot",
            "/auth/forgot-password"
        })
public class ForgotPasswordServlet extends HttpServlet {

    private PasswordResetService passwordResetService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.passwordResetService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("csrfToken", SecurityContext.getOrCreateCsrfToken(request));
        request.getRequestDispatcher("/WEB-INF/views/auth/forgot-password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        PasswordResetService service = passwordResetService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }
        try {
            String email = RequestFields.read(request).get("email");
            if (email == null || email.isBlank()) {
                throw new IdentityException(
                        IdentityError.VALIDATION_FAILED, "Email không được để trống");
            }
            String message = service.forgotPassword(new ForgotPasswordRequestDto(email));
            HttpResponses.data(response, JsonObjectBuilder.object().put("message", message).build());
        } catch (RuntimeException failure) {
            IdentityErrorWriter.write(response, failure, "Đã xảy ra lỗi khi xử lý quên mật khẩu");
        }
    }
}
