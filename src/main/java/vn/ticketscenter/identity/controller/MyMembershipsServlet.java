package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationMembershipService;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@WebServlet(name = "MyMembershipsServlet", urlPatterns = "/api/me/memberships")
public class MyMembershipsServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(MyMembershipsServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UserPrincipal actor = requireUser(req);
            OrganizationMembershipService service = service(getServletContext());

            // read my memberships and write the JSON reply
            List<MembershipSummaryDto> memberships = service.getMyMemberships(actor.actorId());
            String json =
                    memberships.stream()
                            .map(MembershipSummaryDto::toJson)
                            .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, json);
        } catch (AppException rejected) {
            HttpResponses.error(resp, rejected);
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            LOGGER.log(Level.SEVERE, "Membership request failed", ex);
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SERVER_ERROR",
                    "Đã xảy ra lỗi hệ thống.");
        }
    }

    private OrganizationMembershipService service(ServletContext context) {
        IdentityServices services = IdentityServices.from(context);
        if (services == null) {
            throw IdentityErrorWriter.unavailable();
        }
        return services.organizationMembershipService();
    }

    private static UserPrincipal requireUser(HttpServletRequest req) {
        Optional<UserPrincipal> principal = SecurityContext.getCurrentUser(req);
        if (principal.isEmpty()) {
            throw new AppException(
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
        }
        return principal.get();
    }
}
