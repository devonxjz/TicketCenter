package vn.ticketscenter.identity.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.ProfileDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** UI-24: the signed-in user's own profile. The write goes through {@code usp_UpdateMyProfile}. */
@WebServlet(name = "ProfileServlet", urlPatterns = "/api/me/profile")
public class ProfileServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ProfileServlet.class.getName());
    private final AuthService injected;

    public ProfileServlet() { this(null); }

    public ProfileServlet(AuthService authService) { this.injected = authService; }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, false);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        handle(request, response, true);
    }

    private void handle(HttpServletRequest request, HttpServletResponse response, boolean update) throws IOException {
        UserPrincipal principal = SecurityContext.getCurrentUser(request).orElse(null);
        if (principal == null) {
            HttpResponses.error(response, 401, "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }
        AuthService service = injected;
        if (service == null) {
            IdentityServices services = IdentityServices.from(request.getServletContext());
            service = services == null ? null : services.authService();
        }
        if (service == null) {
            HttpResponses.error(response, 503, "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }
        try {
            ProfileDto profile;
            if (update) {
                // Checked here as well as in CsrfFilter so the write never depends on filter wiring alone.
                if (!SecurityContext.validateCsrfToken(request)) {
                    HttpResponses.error(response, 403, "CSRF_TOKEN_INVALID", "Yêu cầu thiếu hoặc mã CSRF không hợp lệ");
                    return;
                }
                Map<String, String> body;
                try { body = JsonObjectParser.parse(request.getReader()); }
                catch (RuntimeException | IOException invalid) {
                    HttpResponses.error(response, 400, "INVALID_JSON", "Định dạng JSON không hợp lệ");
                    return;
                }
                profile = service.updateProfile(principal.actorId(), body.get("fullName"), body.get("phone"));
            } else {
                profile = service.getProfile(principal.actorId());
            }
            if (profile == null) {
                HttpResponses.error(response, 404, "PROFILE_NOT_FOUND", "Không tìm thấy tài khoản");
                return;
            }
            HttpResponses.data(response, "{\"id\":\"" + profile.id() + "\","
                    + "\"email\":" + HttpResponses.jsonString(profile.email()) + ","
                    + "\"userName\":" + HttpResponses.jsonString(profile.userName()) + ","
                    + "\"fullName\":" + HttpResponses.jsonString(profile.fullName()) + ","
                    + "\"phone\":" + HttpResponses.jsonString(profile.phone()) + "}");
        } catch (IllegalArgumentException invalid) {
            HttpResponses.error(response, 400, "PROFILE_INVALID", invalid.getMessage());
        } catch (RuntimeException failure) {
            LOGGER.log(Level.SEVERE, "Profile request failed", failure);
            HttpResponses.error(response, 500, "SERVER_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }
}
