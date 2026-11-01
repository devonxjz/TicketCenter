package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.RequestFields;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.ProfileService;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/** UI-24: the signed-in user's own profile. The write goes through {@code usp_UpdateMyProfile}. */
@WebServlet(name = "ProfileServlet", urlPatterns = "/api/me/profile")
public class ProfileServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ProfileServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            UserPrincipal principal = requireUser(request);
            ProfileDto profile = service().getProfile(principal.actorId());
            writeProfile(response, profile);
        } catch (AppException rejected) {
            HttpResponses.error(response, rejected);
        } catch (IllegalArgumentException invalid) {
            HttpResponses.error(response, 400, "PROFILE_INVALID", invalid.getMessage());
        } catch (RuntimeException failure) {
            LOGGER.log(Level.SEVERE, "Profile request failed", failure);
            HttpResponses.error(response, 500, "SERVER_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            UserPrincipal principal = requireUser(request);
            ProfileService service = service();
            // Checked here as well as in CsrfFilter so the write never depends on filter wiring alone.
            if (!SecurityContext.validateCsrfToken(request)) {
                throw new AppException(
                        403, "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
            }

            // get parameters from the JSON body and save them
            RequestFields body = RequestFields.readJson(request);
            ProfileDto profile =
                    service.updateProfile(
                            principal.actorId(), body.get("fullName"), body.get("phone"));
            writeProfile(response, profile);
        } catch (AppException rejected) {
            HttpResponses.error(response, rejected);
        } catch (IllegalArgumentException invalid) {
            HttpResponses.error(response, 400, "PROFILE_INVALID", invalid.getMessage());
        } catch (RuntimeException failure) {
            LOGGER.log(Level.SEVERE, "Profile request failed", failure);
            HttpResponses.error(response, 500, "SERVER_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }

    private static void writeProfile(HttpServletResponse response, ProfileDto profile)
            throws IOException {
        if (profile == null) {
            throw new AppException(404, "PROFILE_NOT_FOUND", "Không tìm thấy tài khoản");
        }
        HttpResponses.data(response, profile.toJson());
    }

    private ProfileService service() {
        IdentityServices services = IdentityServices.from(getServletContext());
        if (services == null) {
            throw IdentityErrorWriter.unavailable();
        }
        return services.profileService();
    }

    private static UserPrincipal requireUser(HttpServletRequest request) {
        UserPrincipal principal = SecurityContext.getCurrentUser(request).orElse(null);
        if (principal == null) {
            throw new AppException(
                    401, "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
        }
        return principal;
    }
}
