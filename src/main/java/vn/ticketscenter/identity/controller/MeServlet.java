package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.BaseApiServlet;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.ProfileService;

import java.io.IOException;
import java.util.Optional;

@WebServlet(name = "MeServlet", urlPatterns = "/api/me")
public class MeServlet extends BaseApiServlet {

    private ProfileService profileService;

    public MeServlet() {}

    public MeServlet(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Override
    protected void doRequest(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            respondUnauthorized(res);
            return;
        }

        ProfileService service = profileService(req);
        if (service == null) {
            IdentityErrorWriter.databaseUnavailable(res);
            return;
        }

        CurrentUserDto user = service.getCurrentUser(principalOpt.get().actorId());
        if (user == null) {
            SecurityContext.clearSession(req);
            respondUnauthorized(res);
            return;
        }

        respondData(res, user.toJson());
    }

    private ProfileService profileService(HttpServletRequest req) {
        if (profileService != null) return profileService;
        IdentityServices services = IdentityServices.from(req.getServletContext());
        return services == null ? null : services.profileService();
    }
}
