package vn.ticketscenter.identity.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.config.IdentityServices;
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

@WebServlet(name = "OrganizationMembersServlet", urlPatterns = "/api/organizations-members-internal/*")
public class OrganizationMembersServlet extends HttpServlet {

    private static final Pattern MEMBERS_PATTERN =
            Pattern.compile("^/api/organizations/([^/]+)/members(?:/([^/]+)/(role|deactivate|activate))?/?$");

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
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        String path = req.getRequestURI().substring(req.getContextPath().length());
        Matcher matcher = MEMBERS_PATTERN.matcher(path);
        if (!matcher.matches() || matcher.group(3) != null) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Đường dẫn không hợp lệ");
            return;
        }

        UUID orgId = parseUuid(matcher.group(1));
        if (orgId == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Mã tổ chức không hợp lệ");
            return;
        }

        OrganizationMembershipService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        try {
            List<OrganizationMemberDto> members = service.getOrganizationMembers(principalOpt.get().actorId(), orgId);
            String json = members.stream().map(OrganizationMemberDto::toJson).collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(resp, json);
        } catch (SecurityException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(OrganizationMembersServlet.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Membership request failed", ex);
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Optional<UserPrincipal> principalOpt = SecurityContext.getCurrentUser(req);
        if (principalOpt.isEmpty()) {
            HttpResponses.error(resp, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHENTICATED", "Bạn chưa đăng nhập hoặc phiên làm việc đã hết hạn");
            return;
        }

        if (!SecurityContext.validateCsrfToken(req)) {
            HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN, "CSRF_TOKEN_INVALID", "Yêu cầu CSRF token hợp lệ");
            return;
        }

        String path = req.getRequestURI().substring(req.getContextPath().length());
        Matcher matcher = MEMBERS_PATTERN.matcher(path);
        if (!matcher.matches()) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Đường dẫn không hợp lệ");
            return;
        }

        UUID orgId = parseUuid(matcher.group(1));
        if (orgId == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_PATH", "Mã tổ chức không hợp lệ");
            return;
        }

        OrganizationMembershipService service = getService(req.getServletContext());
        if (service == null) {
            HttpResponses.error(resp, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "Hệ thống cơ sở dữ liệu chưa sẵn sàng");
            return;
        }

        UUID actorId = principalOpt.get().actorId();
        String action = matcher.group(3);

        try {
            if (action == null) {
                Map<String, String> body = JsonObjectParser.parse(req.getReader());
                String userName = body.get("userName");
                OrganizationRole role = parseRole(body.get("role"));
                AddMemberRequestDto dto = new AddMemberRequestDto(userName, role);
                OrganizationMemberDto member = service.addMember(actorId, orgId, dto);
                HttpResponses.created(resp, member.toJson());
            } else {
                UUID targetUserId = parseUuid(matcher.group(2));
                if (targetUserId == null) {
                    HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_UUID", "Mã người dùng không hợp lệ");
                    return;
                }
                switch (action) {
                    case "role" -> {
                        Map<String, String> body = JsonObjectParser.parse(req.getReader());
                        OrganizationRole role = parseRole(body.get("role"));
                        ChangeMemberRoleRequestDto dto = new ChangeMemberRoleRequestDto(role);
                        OrganizationMemberDto updated = service.changeRole(actorId, orgId, targetUserId, dto);
                        HttpResponses.data(resp, updated.toJson());
                    }
                    case "deactivate" -> {
                        OrganizationMemberDto updated = service.deactivateMember(actorId, orgId, targetUserId);
                        HttpResponses.data(resp, updated.toJson());
                    }
                    case "activate" -> {
                        OrganizationMemberDto updated = service.activateMember(actorId, orgId, targetUserId);
                        HttpResponses.data(resp, updated.toJson());
                    }
                    default -> HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_ACTION", "Hành động không hợp lệ");
                }
            }
        } catch (SecurityException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", ex.getMessage());
        } catch (IllegalStateException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("MEMBER_ALREADY_EXISTS")) {
                HttpResponses.error(resp, HttpServletResponse.SC_CONFLICT, "MEMBER_ALREADY_EXISTS", ex.getMessage());
            } else if (ex.getMessage() != null && ex.getMessage().contains("CANNOT_DEACTIVATE_LAST_MANAGER")) {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "CANNOT_DEACTIVATE_LAST_MANAGER", ex.getMessage());
            } else {
                HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
            }
        } catch (IllegalArgumentException ex) {
            HttpResponses.error(resp, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST", ex.getMessage());
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(OrganizationMembersServlet.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Membership request failed", ex);
            HttpResponses.error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Đã xảy ra lỗi hệ thống.");
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static OrganizationRole parseRole(String roleStr) {
        if (roleStr == null) {
            throw new IllegalArgumentException("Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
        }
        try {
            return OrganizationRole.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
        }
    }
}
