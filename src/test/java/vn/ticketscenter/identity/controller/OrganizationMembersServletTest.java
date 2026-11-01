package vn.ticketscenter.identity.controller;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.dto.AddMemberRequestDto;
import vn.ticketscenter.identity.dto.ChangeMemberRoleRequestDto;
import vn.ticketscenter.identity.dto.MembershipSummaryDto;
import vn.ticketscenter.identity.dto.OrganizationMemberDto;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationMembershipService;
import vn.ticketscenter.identity.service.OrganizationMembershipServiceImpl;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrganizationMembersServletTest {

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private StringWriter responseBuffer;
    private AtomicInteger responseStatus;

    private UUID actorId;
    private UUID orgId;
    private UUID targetUserId;
    private UserPrincipal principal;

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        responseBuffer = new StringWriter();
        responseStatus = new AtomicInteger(200);

        actorId = UUID.randomUUID();
        orgId = UUID.randomUUID();
        targetUserId = UUID.randomUUID();
        principal = new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1, OrganizationRole.MANAGER);

        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, principal);
        requestAttributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, principal);

        String csrf = "valid-csrf-token";
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, csrf);
        requestHeaders.put(SecurityContext.CSRF_HEADER_NAME, csrf);
    }

    private HttpServletRequest createRequest(String method, String uri, String body) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getSession" -> createSession();
                    case "getReader" -> new BufferedReader(new StringReader(body != null ? body : ""));
                    default -> null;
                }
        );
    }

    private HttpSession createSession() {
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private HttpServletResponse createResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        responseStatus.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "getWriter" -> new PrintWriter(responseBuffer);
                    case "setContentType", "setCharacterEncoding", "setHeader" -> null;
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("MyMembershipsServlet: GET /api/me/memberships trả về 200 OK")
    void testMyMembershipsServlet_Success() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public List<MembershipSummaryDto> getMyMemberships(UUID actor) {
                return List.of(new MembershipSummaryDto(orgId, "Cty Âm Nhạc", OrganizationRole.MANAGER, true));
            }
        };

        MyMembershipsServlet servlet = TestServlets.init(new MyMembershipsServlet(), fakeService);
        HttpServletRequest req = createRequest("GET", "/api/me/memberships", null);
        HttpServletResponse resp = createResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("Cty Âm Nhạc"));
        assertTrue(body.contains("MANAGER"));
    }

    @Test
    @DisplayName("OrganizationMembersServlet: GET /api/organizations/{orgId}/members thành công")
    void testOrganizationMembersServlet_GetMembers_Success() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public List<OrganizationMemberDto> getOrganizationMembers(UUID actor, UUID oId) {
                return List.of(new OrganizationMemberDto(
                        UUID.randomUUID(), targetUserId, "staff1", "Staff One", "staff@vwm.vn",
                        OrganizationRole.CHECK_IN_STAFF, true, Instant.now()
                ));
            }
        };

        OrganizationMembersServlet servlet = TestServlets.init(new OrganizationMembersServlet(), fakeService);
        HttpServletRequest req = createRequest("GET", "/api/organizations/" + orgId + "/members", null);
        HttpServletResponse resp = createResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("staff1"));
        assertTrue(body.contains("CHECK_IN_STAFF"));
    }

    @Test
    @DisplayName("OrganizationMembersServlet: POST thêm thành viên thành công (201 Created)")
    void testOrganizationMembersServlet_AddMember_Success() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public OrganizationMemberDto addMember(UUID actor, UUID oId, AddMemberRequestDto dto) {
                return new OrganizationMemberDto(
                        UUID.randomUUID(), targetUserId, dto.userName(), "Staff One", "staff@vwm.vn",
                        dto.role(), true, Instant.now()
                );
            }
        };

        OrganizationMembersServlet servlet = TestServlets.init(new OrganizationMembersServlet(), fakeService);
        String jsonBody = "{\"userName\":\"staff1\",\"role\":\"CHECK_IN_STAFF\"}";
        HttpServletRequest req = createRequest("POST", "/api/organizations/" + orgId + "/members", jsonBody);
        HttpServletResponse resp = createResponse();

        servlet.doPost(req, resp);

        assertEquals(201, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("staff1"));
    }

    @Test
    @DisplayName("OrganizationMembersServlet: POST thêm thành viên trùng lặp -> 409 Conflict")
    void testOrganizationMembersServlet_AddMember_Conflict() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public OrganizationMemberDto addMember(UUID actor, UUID oId, AddMemberRequestDto dto) {
                throw new IllegalStateException("MEMBER_ALREADY_EXISTS: Thành viên đã đang hoạt động trong tổ chức");
            }
        };

        OrganizationMembersServlet servlet = TestServlets.init(new OrganizationMembersServlet(), fakeService);
        String jsonBody = "{\"userName\":\"staff1\",\"role\":\"CHECK_IN_STAFF\"}";
        HttpServletRequest req = createRequest("POST", "/api/organizations/" + orgId + "/members", jsonBody);
        HttpServletResponse resp = createResponse();

        servlet.doPost(req, resp);

        assertEquals(409, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("MEMBER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("OrganizationMembersServlet: Hạ quyền Manager cuối cùng -> 400 Bad Request CANNOT_DEACTIVATE_LAST_MANAGER")
    void testMemberRoleServlet_DemoteLastManager_Throws400() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public OrganizationMemberDto changeRole(UUID actor, UUID oId, UUID target, ChangeMemberRoleRequestDto dto) {
                throw new IllegalStateException("CANNOT_DEACTIVATE_LAST_MANAGER: Không thể hạ quyền Quản lý duy nhất còn lại của tổ chức");
            }
        };

        OrganizationMembersServlet servlet = TestServlets.init(new OrganizationMembersServlet(), fakeService);
        String jsonBody = "{\"role\":\"CHECK_IN_STAFF\"}";
        HttpServletRequest req = createRequest("POST", "/api/organizations/" + orgId + "/members/" + targetUserId + "/role", jsonBody);
        HttpServletResponse resp = createResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
    }

    @Test
    @DisplayName("OrganizationMembersServlet: Vô hiệu hóa Manager cuối cùng -> 400 Bad Request CANNOT_DEACTIVATE_LAST_MANAGER")
    void testMemberStatusServlet_DeactivateLastManager_Throws400() throws Exception {
        OrganizationMembershipService fakeService = new OrganizationMembershipServiceImpl() {
            @Override
            public OrganizationMemberDto deactivateMember(UUID actor, UUID oId, UUID target) {
                throw new IllegalStateException("CANNOT_DEACTIVATE_LAST_MANAGER: Không thể vô hiệu hóa Quản lý duy nhất còn lại của tổ chức");
            }
        };

        OrganizationMembersServlet servlet = TestServlets.init(new OrganizationMembersServlet(), fakeService);
        HttpServletRequest req = createRequest("POST", "/api/organizations/" + orgId + "/members/" + targetUserId + "/deactivate", null);
        HttpServletResponse resp = createResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("CANNOT_DEACTIVATE_LAST_MANAGER"));
    }
}
