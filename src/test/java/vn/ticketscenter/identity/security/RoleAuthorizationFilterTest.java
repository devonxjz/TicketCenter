package vn.ticketscenter.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.OrganizationRole;
import vn.ticketscenter.identity.model.PlatformRole;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RoleAuthorizationFilter - Kiểm tra phân quyền truy cập theo vai trò")
class RoleAuthorizationFilterTest {

    private RoleAuthorizationFilter filter;
    private Map<String, Object> sessionAttributes;
    private AtomicBoolean chainExecuted;
    private AtomicInteger responseStatus;
    private AtomicReference<String> redirectedUrl;
    private AtomicReference<String> sendErrorMessage;

    @BeforeEach
    void setUp() {
        filter = new RoleAuthorizationFilter();
        sessionAttributes = new HashMap<>();
        chainExecuted = new AtomicBoolean(false);
        responseStatus = new AtomicInteger(200);
        redirectedUrl = new AtomicReference<>(null);
        sendErrorMessage = new AtomicReference<>(null);
    }

    private HttpSession createMockSession() {
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String uri, UserPrincipal principal) {
        HttpSession session = createMockSession();
        if (principal != null) {
            sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, principal);
        }

        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getQueryString" -> null;
                    case "getAttribute" -> {
                        if (SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0])) {
                            yield principal;
                        }
                        yield null;
                    }
                    case "getSession" -> session;
                    default -> null;
                }
        );
    }

    private HttpServletResponse createMockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "sendRedirect" -> {
                        redirectedUrl.set((String) args[0]);
                        yield null;
                    }
                    case "sendError" -> {
                        responseStatus.set((Integer) args[0]);
                        if (args.length > 1) {
                            sendErrorMessage.set((String) args[1]);
                        }
                        yield null;
                    }
                    case "setStatus" -> {
                        responseStatus.set((Integer) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private FilterChain createMockChain() {
        return (request, response) -> chainExecuted.set(true);
    }

    @Test
    @DisplayName("Chưa đăng nhập -> Chuyển hướng đến /auth/login khi vào /admin hoặc /organizer")
    void testUnauthenticatedRedirectsToLogin() throws Exception {
        HttpServletRequest req = createMockRequest("/admin/dashboard", null);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get());
        assertNotNull(redirectedUrl.get());
        assertTrue(redirectedUrl.get().contains("/auth/login?redirect="));
    }

    @Test
    @DisplayName("Admin: Vào được /admin/dashboard và /organizer/dashboard")
    void testAdminAccessAllowed() throws Exception {
        UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), PlatformRole.ADMIN, 0);

        // 1. /admin/dashboard
        HttpServletRequest req1 = createMockRequest("/admin/dashboard", admin);
        HttpServletResponse resp1 = createMockResponse();
        filter.doFilter(req1, resp1, createMockChain());
        assertTrue(chainExecuted.get(), "Admin must access /admin/dashboard");

        // 2. /organizer/dashboard
        chainExecuted.set(false);
        HttpServletRequest req2 = createMockRequest("/organizer/dashboard", admin);
        HttpServletResponse resp2 = createMockResponse();
        filter.doFilter(req2, resp2, createMockChain());
        assertTrue(chainExecuted.get(), "Admin must access /organizer/dashboard");
    }

    @Test
    @DisplayName("Customer: Bị từ chối 403 khi vào /admin/dashboard")
    void testCustomerCannotAccessAdmin() throws Exception {
        UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, null);
        HttpServletRequest req = createMockRequest("/admin/dashboard", customer);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get(), "Customer must NOT access /admin/dashboard");
        assertEquals(403, responseStatus.get());
    }

    @Test
    void customerCanOpenOrganizationApplicationWithoutManagementAccess() throws Exception {
        UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, null);
        filter.doFilter(createMockRequest("/organizer/request", customer), createMockResponse(), createMockChain());
        assertTrue(chainExecuted.get());
    }

    @Test
    @DisplayName("Customer: Bị từ chối 403 khi vào /organizer/dashboard hoặc /organizer/checkin-select")
    void testCustomerCannotAccessOrganizerOrCheckin() throws Exception {
        UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, null);

        // /organizer/dashboard
        HttpServletRequest req1 = createMockRequest("/organizer/dashboard", customer);
        HttpServletResponse resp1 = createMockResponse();
        filter.doFilter(req1, resp1, createMockChain());
        assertFalse(chainExecuted.get());
        assertEquals(403, responseStatus.get());

        // /organizer/checkin-select
        chainExecuted.set(false);
        HttpServletRequest req2 = createMockRequest("/organizer/checkin-select", customer);
        HttpServletResponse resp2 = createMockResponse();
        filter.doFilter(req2, resp2, createMockChain());
        assertFalse(chainExecuted.get(), "Customer must NOT access checkin");
        assertEquals(403, responseStatus.get());
    }

    @Test
    @DisplayName("Staff: Bị từ chối 403 khi vào /admin/dashboard")
    void testStaffCannotAccessAdmin() throws Exception {
        UserPrincipal staff = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, OrganizationRole.CHECK_IN_STAFF);
        HttpServletRequest req = createMockRequest("/admin/dashboard", staff);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get(), "Staff must NOT access /admin/dashboard");
        assertEquals(403, responseStatus.get());
    }

    @Test
    @DisplayName("Staff: Vào được /organizer/checkin-select và /organizer/checkin-scanner")
    void testStaffCanAccessCheckin() throws Exception {
        UserPrincipal staff = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, OrganizationRole.CHECK_IN_STAFF);

        // Checkin Select
        HttpServletRequest req1 = createMockRequest("/organizer/checkin-select", staff);
        HttpServletResponse resp1 = createMockResponse();
        filter.doFilter(req1, resp1, createMockChain());
        assertTrue(chainExecuted.get(), "Staff must access /organizer/checkin-select");

        // Checkin Scanner
        chainExecuted.set(false);
        HttpServletRequest req2 = createMockRequest("/organizer/checkin-scanner", staff);
        HttpServletResponse resp2 = createMockResponse();
        filter.doFilter(req2, resp2, createMockChain());
        assertTrue(chainExecuted.get(), "Staff must access /organizer/checkin-scanner");
    }

    @Test
    @DisplayName("Staff: Bị chuyển hướng về /organizer/checkin-select khi cố vào /organizer/dashboard hoặc /events")
    void testStaffRedirectedFromManagementPages() throws Exception {
        UserPrincipal staff = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, OrganizationRole.CHECK_IN_STAFF);
        HttpServletRequest req = createMockRequest("/organizer/dashboard", staff);
        HttpServletResponse resp = createMockResponse();

        filter.doFilter(req, resp, createMockChain());

        assertFalse(chainExecuted.get(), "Staff must NOT access /organizer/dashboard");
        assertEquals("/organizer/checkin-select", redirectedUrl.get());
    }

    @Test
    @DisplayName("Manager: Vào được /organizer/dashboard và /organizer/checkin-select, nhưng KHÔNG vào được /admin")
    void testManagerAccess() throws Exception {
        UserPrincipal manager = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0, OrganizationRole.MANAGER);

        // /organizer/dashboard -> ALLOW
        HttpServletRequest req1 = createMockRequest("/organizer/dashboard", manager);
        HttpServletResponse resp1 = createMockResponse();
        filter.doFilter(req1, resp1, createMockChain());
        assertTrue(chainExecuted.get());

        // /organizer/checkin-select -> ALLOW
        chainExecuted.set(false);
        HttpServletRequest req2 = createMockRequest("/organizer/checkin-select", manager);
        HttpServletResponse resp2 = createMockResponse();
        filter.doFilter(req2, resp2, createMockChain());
        assertTrue(chainExecuted.get());

        // /admin/dashboard -> DENY 403
        chainExecuted.set(false);
        HttpServletRequest req3 = createMockRequest("/admin/dashboard", manager);
        HttpServletResponse resp3 = createMockResponse();
        filter.doFilter(req3, resp3, createMockChain());
        assertFalse(chainExecuted.get(), "Manager must NOT access /admin/dashboard");
        assertEquals(403, responseStatus.get());
    }
}
