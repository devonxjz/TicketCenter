package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@WebServlet(
        name = "OrganizationMembersServlet",
        urlPatterns = "/api/organizations-members-internal/*")
public class OrganizationMembersServlet extends HttpServlet {

    private static final Pattern MEMBERS_PATTERN =
            Pattern.compile(
                    "^/api/organizations/([^/]+)/members(?:/([^/]+)/(role|deactivate|activate))?/?$");

    private OrganizationMembershipService membershipService;

    public OrganizationMembersServlet() {}

    public OrganizationMembersServlet(OrganizationMembershipService membershipService) {
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

        String path = PathIds.path(req);
        Matcher matcher = MEMBERS_PATTERN.matcher(path);
        if (!matcher.matches() || matcher.group(3) != null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PATH",
                    "Đường dẫn không hợp lệ");
            return;
        }

        UUID orgId = PathIds.uuidOrNull(matcher.group(1));
        if (orgId == null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PATH",
                    "Mã tổ chức không hợp lệ");
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
            List<OrganizationMemberDto> members =
                    service.getOrganizationMembers(principalOpt.get().actorId(), orgId);
            String json =
                    members.stream()
                            .map(OrganizationMemberDto::toJson)
                            .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, json);
        } catch (Exception ex) {
            handleException(resp, ex);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "UNAUTHENTICATED",
                    "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        if (!SecurityContext.validateCsrfToken(req)) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_FORBIDDEN,
                    "CSRF_TOKEN_INVALID",
                    "Yêu cầu CSRF token hợp lệ");
            return;
        }

        String path = PathIds.path(req);
        Matcher matcher = MEMBERS_PATTERN.matcher(path);
        if (!matcher.matches()) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PATH",
                    "Đường dẫn không hợp lệ");
            return;
        }

        UUID orgId = PathIds.uuidOrNull(matcher.group(1));
        if (orgId == null) {
            HttpResponses.error(
                    resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "INVALID_PATH",
                    "Mã tổ chức không hợp lệ");
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

        UUID actorId = principalOpt.get().actorId();
        String action = matcher.group(3);

        try {
            if (action == null) {
                Map<String, String> body = JsonObjectParser.parse(req.getReader());
                OrganizationRole role = parseRole(body.get("role"));
                AddMemberRequestDto dto = new AddMemberRequestDto(body.get("userName"), role);
                OrganizationMemberDto member = service.addMember(actorId, orgId, dto);
                HttpResponses.created(resp, member.toJson());
                return;
            }

            UUID targetUserId = PathIds.uuidOrNull(matcher.group(2));
            if (targetUserId == null) {
                HttpResponses.error(
                        resp,
                        HttpServletResponse.SC_BAD_REQUEST,
                        "INVALID_UUID",
                        "Mã người dùng không hợp lệ");
                return;
            }

            OrganizationMemberDto updated = switch (action) {
                case "role" -> {
                    Map<String, String> body = JsonObjectParser.parse(req.getReader());
                    OrganizationRole role = parseRole(body.get("role"));
                    yield service.changeRole(actorId, orgId, targetUserId, new ChangeMemberRoleRequestDto(role));
                }
                case "deactivate" -> service.deactivateMember(actorId, orgId, targetUserId);
                case "activate" -> service.activateMember(actorId, orgId, targetUserId);
                default -> {
                    HttpResponses.error(
                            resp,
                            HttpServletResponse.SC_BAD_REQUEST,
                            "INVALID_ACTION",
                            "Hành động không hợp lệ");
                    yield null;
                }
            };
            if (updated != null) {
                HttpResponses.data(resp, updated.toJson());
            }
        } catch (IllegalStateException ex) {
            membershipConflict(resp, ex);
        } catch (Exception ex) {
            handleException(resp, ex);
        }
    }

    private static void handleException(HttpServletResponse resp, Exception ex) throws IOException {
        if (ex instanceof SecurityException) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
        } else if (ex instanceof IllegalArgumentException) {
            HttpResponses.error(
                    resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        } else {
            java.util.logging.Logger.getLogger(OrganizationMembersServlet.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Membership request failed", ex);
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
