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

    private ProfileService profileService;

    public MeServlet() {}

    public MeServlet(ProfileService profileService) {
        this.profileService = profileService;
    }

    private ProfileService profileService(ServletContext context) {
        if (profileService != null) return profileService;
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.profileService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        ProfileService service = profileService(req.getServletContext());
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(resp);
            return;
        }

        CurrentUserDto user = service.getCurrentUser(principalOpt.get().actorId());
        if (user == null) {
            SecurityContext.clearSession(req);
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Tài khoản không tồn tại hoặc đã bị xóa");
            return;
        }

        HttpResponses.data(resp, user.toJson());
    }
}
