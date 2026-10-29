package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.ProfileService;

import java.io.IOException;
import java.util.Optional;

@WebServlet(name = "MeServlet", urlPatterns = "/api/me")
public class MeServlet extends HttpServlet {

    private ProfileService profileService(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.profileService();
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(request);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        ProfileService service = profileService(getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(response);
            return;
        }

        CurrentUserDto user = service.getCurrentUser(principalOpt.get().actorId());
        if (user == null) {
            SecurityContext.clearSession(request);
            HttpResponses.error(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Tài khoản không tồn tại hoặc đã bị xóa");
            return;
        }

        HttpResponses.data(response, user.toJson());
    }
}
