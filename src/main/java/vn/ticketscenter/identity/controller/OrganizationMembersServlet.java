package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.error.AppException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.config.identity.IdentityServices;
import vn.ticketscenter.identity.controller.support.IdentityErrorWriter;
import vn.ticketscenter.identity.controller.support.PathIds;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationMembershipService;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@WebServlet(
        name = "OrganizationMembersServlet",
        urlPatterns = "/api/organizations-members-internal/*")
public class OrganizationMembersServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(OrganizationMembersServlet.class.getName());

    private static final Pattern MEMBERS_PATTERN =
            Pattern.compile(
                    "^/api/organizations/([^/]+)/members(?:/([^/]+)/(role|deactivate|activate))?/?$");

    private OrganizationMembershipService membershipService;

    public OrganizationMembersServlet() {}

    public OrganizationMembersServlet(OrganizationMembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UserPrincipal actor = requireUser(req);
            Matcher matcher = MEMBERS_PATTERN.matcher(PathIds.path(req));
            if (!matcher.matches() || matcher.group(3) != null) {
                throw invalidPath();
            }
            UUID orgId = organizationId(matcher.group(1));
            OrganizationMembershipService service = service(req.getServletContext());

            // read the members and write the JSON reply
            List<OrganizationMemberDto> members =
                    service.getOrganizationMembers(actor.actorId(), orgId);
            String json =
                    members.stream()
                            .map(OrganizationMemberDto::toJson)
                            .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, json);
        } catch (AppException rejected) {
            HttpResponses.error(resp, rejected);
        } catch (Exception ex) {
            handleException(resp, ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            UserPrincipal actor = requireUser(req);
            if (!SecurityContext.validateCsrfToken(req)) {
                throw new AppException(
                        HttpServletResponse.SC_FORBIDDEN,
                        "CSRF_TOKEN_INVALID",
                        "Yêu cầu CSRF token hợp lệ");
            }
            Matcher matcher = MEMBERS_PATTERN.matcher(PathIds.path(req));
            if (!matcher.matches()) {
                throw invalidPath();
            }
            UUID orgId = organizationId(matcher.group(1));
            OrganizationMembershipService service = service(req.getServletContext());

            // perform action and write the JSON reply
            String action = matcher.group(3);
            if (action == null) {
                OrganizationMemberDto member = addMember(req, service, actor, orgId);
                HttpResponses.created(resp, member.toJson());
            } else {
                UUID targetUserId = targetUserId(matcher.group(2));
                OrganizationMemberDto updated =
                        updateMember(req, service, actor, orgId, targetUserId, action);
                HttpResponses.data(resp, updated.toJson());
            }
        } catch (AppException rejected) {
            HttpResponses.error(resp, rejected);
        } catch (IllegalStateException ex) {
            membershipConflict(resp, ex);
        } catch (Exception ex) {
            handleException(resp, ex);
        }
    }

    private static OrganizationMemberDto addMember(
            HttpServletRequest req,
            OrganizationMembershipService service,
            UserPrincipal actor,
            UUID orgId)
            throws IOException {
        Map<String, String> body = JsonObjectParser.parse(req.getReader());
        OrganizationRole role = parseRole(body.get("role"));
        AddMemberRequestDto dto = new AddMemberRequestDto(body.get("userName"), role);
        return service.addMember(actor.actorId(), orgId, dto);
    }

    private static OrganizationMemberDto updateMember(
            HttpServletRequest req,
            OrganizationMembershipService service,
            UserPrincipal actor,
            UUID orgId,
            UUID targetUserId,
            String action)
            throws IOException {
        UUID actorId = actor.actorId();
        if (action.equals("role")) {
            Map<String, String> body = JsonObjectParser.parse(req.getReader());
            OrganizationRole role = parseRole(body.get("role"));
            return service.changeRole(
                    actorId, orgId, targetUserId, new ChangeMemberRoleRequestDto(role));
        } else if (action.equals("deactivate")) {
            return service.deactivateMember(actorId, orgId, targetUserId);
        } else if (action.equals("activate")) {
            return service.activateMember(actorId, orgId, targetUserId);
        }
        throw new AppException(
                HttpServletResponse.SC_BAD_REQUEST, "INVALID_ACTION", "Hành động không hợp lệ");
    }

    private OrganizationMembershipService service(ServletContext context) {
        if (membershipService != null) {
            return membershipService;
        }
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

    private static UUID organizationId(String idText) {
        UUID orgId = PathIds.uuidOrNull(idText);
        if (orgId == null) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Mã tổ chức không hợp lệ");
        }
        return orgId;
    }

    private static UUID targetUserId(String idText) {
        UUID userId = PathIds.uuidOrNull(idText);
        if (userId == null) {
            throw new AppException(
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_UUID",
                    "Mã người dùng không hợp lệ");
        }
        return userId;
    }

    private static AppException invalidPath() {
        return new AppException(
                HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Đường dẫn không hợp lệ");
    }

    private static void handleException(HttpServletResponse resp, Exception ex) throws IOException {
        if (ex instanceof SecurityException) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
        } else if (ex instanceof IllegalArgumentException) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        } else {
            LOGGER.log(Level.SEVERE, "Membership request failed", ex);
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "SERVER_ERROR",
                    "Đã xảy ra lỗi hệ thống.");
        }
    }

    private static void membershipConflict(HttpServletResponse resp, IllegalStateException ex)
            throws IOException {
        String msg = ex.getMessage();
        if (msg != null && msg.startsWith("MEMBER_ALREADY_EXISTS")) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_CONFLICT,
                    "MEMBER_ALREADY_EXISTS",
                    msg);
        } else if (msg != null && msg.contains("CANNOT_DEACTIVATE_LAST_MANAGER")) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "CANNOT_DEACTIVATE_LAST_MANAGER",
                    msg);
        } else {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", msg);
        }
    }

    private static OrganizationRole parseRole(String roleStr) {
        try {
            if (roleStr != null) {
                return OrganizationRole.valueOf(roleStr);
            }
        } catch (IllegalArgumentException ignored) {}
        throw new IllegalArgumentException(
                "Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
    }
}
