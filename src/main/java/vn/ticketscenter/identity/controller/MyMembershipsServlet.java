package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.identity.IdentityServices;
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

    private OrganizationMembershipService membershipService;

    public MyMembershipsServlet() {}

    public MyMembershipsServlet(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    private OrganizationMembershipService getService(ServletContext context) {
        if (this.membershipService != null) {
            return this.membershipService;
        }
        IdentityServices services = IdentityServices.from(context);
        return services == null ? null : services.organizationMembershipService();
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

        OrganizationMembershipService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "DATABASE_UNAVAILABLE",
                    "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        try {
            List<MembershipSummaryDto> memberships =
                    service.getMyMemberships(principalOpt.get().actorId());
            String json =
                    memberships.stream()
                            .map(
                                    m ->
                                            "{\"organizationId\":"
                                                    + HttpResponses.jsonString(
                                                            m.organizationId().toString())
                                                    + ",\"organizationName\":"
                                                    + HttpResponses.jsonString(m.organizationName())
                                                    + ",\"role\":"
                                                    + HttpResponses.jsonString(m.role().name())
                                                    + ",\"isActive\":"
                                                    + m.isActive()
                                                    + "}")
                            .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, json);
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
}
