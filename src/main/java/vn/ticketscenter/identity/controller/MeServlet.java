package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dto.CurrentUserDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;

import java.io.IOException;
import java.util.Optional;

@WebServlet(name = "MeServlet", urlPatterns = "/api/me")
public class MeServlet extends HttpServlet {

    private AuthService authService;

    public MeServlet() {}

    public MeServlet(AuthService authService) {
        this.authService = authService;
    }

    private AuthService authService(ServletContext context) {
        if (authService != null) return authService;
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.authService();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        AuthService service = authService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        CurrentUserDto user = service.getCurrentUser(principalOpt.get().actorId());
        if (user == null) {
            SecurityContext.clearSession(req);
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED", "Tài khoản không tồn tại hoặc đã bị xóa");
            return;
        }

        String json = "{\"id\":\"" + user.id() + "\","
                + "\"email\":" + HttpResponses.jsonString(user.email()) + ","
                + "\"userName\":" + HttpResponses.jsonString(user.userName()) + ","
                + "\"fullName\":" + HttpResponses.jsonString(user.fullName()) + ","
                + "\"platformRole\":\"" + user.platformRole().name() + "\","
                + "\"admin\":" + user.admin() + "}";

        HttpResponses.data(resp, json);
    }
}
