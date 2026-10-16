package vn.ticketscenter.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.identity.error.IdentityError;
import vn.ticketscenter.identity.error.IdentityException;
import vn.ticketscenter.identity.dto.OrganizationRequestCreateDto;
import vn.ticketscenter.identity.dto.OrganizationRequestDetailDto;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.dao.OrganizationRepository;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.security.CsrfFilter;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.OrganizationRequestService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationRequestWebFlowTest {

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private StringWriter responseBuffer;
    private AtomicInteger responseStatus;
    private AtomicBoolean chainExecuted;

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        responseBuffer = new StringWriter();
        responseStatus = new AtomicInteger(200);
        chainExecuted = new AtomicBoolean(false);
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
                    case "removeAttribute" -> {
                        sessionAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "getId" -> "webflow-session-id";
                    default -> null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String uri, boolean hasSession, String jsonBody) {
        HttpSession session = hasSession ? createMockSession() : null;
        Map<String, Object> attrs = new HashMap<>(requestAttributes);
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getAttribute" -> attrs.get((String) args[0]);
                    case "setAttribute" -> {
                        attrs.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getSession" -> {
                        if (args != null && args.length == 1) {
                            boolean create = (Boolean) args[0];
                            if (!create) yield session;
                        }
                        yield session != null ? session : createMockSession();
                    }
                    case "getContentType" -> "application/json";
                    case "getReader" -> new BufferedReader(new StringReader(jsonBody != null ? jsonBody : ""));
                    case "getServletContext" -> null;
                    default -> null;
                }
        );
    }

    private HttpServletResponse createMockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        responseStatus.set((Integer) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "setHeader", "setContentType", "setCharacterEncoding" -> null;
                    case "getWriter" -> new PrintWriter(responseBuffer);
                    default -> null;
                }
        );
    }

    @Test
    @DisplayName("ORG-01: Valid request submission creates pending organization request and returns 201 Created")
    void testOrg01_ValidSubmissionSuccess() throws Exception {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        String csrfToken = UUID.randomUUID().toString();
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, csrfToken);
        requestHeaders.put("X-CSRF-Token", csrfToken);

        OrganizationRequestDetailDto detailDto = new OrganizationRequestDetailDto(
                orgId, "Saigon Music Production", "contact@sgmusic.vn", "0901234567",
                "Concert and live event management", OrganizationStatus.PENDING_APPROVAL,
                actorId, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                assertEquals(actorId, id);
                assertEquals("Saigon Music Production", dto.name());
                return detailDto;
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        CsrfFilter csrfFilter = new CsrfFilter();

        String body = "{\"name\":\"Saigon Music Production\",\"contactEmail\":\"contact@sgmusic.vn\",\"contactPhone\":\"0901234567\",\"description\":\"Concert and live event management\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        // Pass through CsrfFilter to Servlet
        csrfFilter.doFilter(req, resp, (request, response) -> {
            chainExecuted.set(true);
            try {
                servlet.doPost((HttpServletRequest) request, (HttpServletResponse) response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(chainExecuted.get(), "CSRF filter should have passed the valid request");
        assertEquals(201, responseStatus.get(), "Should return HTTP 201 Created");
        String json = responseBuffer.toString();
        assertTrue(json.contains("Saigon Music Production"));
        assertTrue(json.contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("ORG-02: Missing required name or email fails with 400 Bad Request")
    void testOrg02_MissingRequiredFieldsFails() throws Exception {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        String csrfToken = UUID.randomUUID().toString();
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, csrfToken);
        requestHeaders.put("X-CSRF-Token", csrfToken);

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                dto.validate();
                return null;
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        CsrfFilter csrfFilter = new CsrfFilter();

        // Invalid input with missing name
        String body = "{\"name\":\"\",\"contactEmail\":\"contact@sgmusic.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        csrfFilter.doFilter(req, resp, (request, response) -> {
            chainExecuted.set(true);
            try {
                servlet.doPost((HttpServletRequest) request, (HttpServletResponse) response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(chainExecuted.get());
        assertEquals(400, responseStatus.get(), "Should return HTTP 400 for blank name");
        assertTrue(responseBuffer.toString().contains("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("ORG-03: Submitting when user already has an active pending request returns 409 Conflict")
    void testOrg03_DuplicatePendingRequestReturns409() throws Exception {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        String csrfToken = UUID.randomUUID().toString();
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, csrfToken);
        requestHeaders.put("X-CSRF-Token", csrfToken);

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                throw new IdentityException(IdentityError.ORGANIZATION_REQUEST_EXISTS,
                        "Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        CsrfFilter csrfFilter = new CsrfFilter();

        String body = "{\"name\":\"Another Org\",\"contactEmail\":\"another@sgmusic.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        csrfFilter.doFilter(req, resp, (request, response) -> {
            chainExecuted.set(true);
            try {
                servlet.doPost((HttpServletRequest) request, (HttpServletResponse) response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertTrue(chainExecuted.get());
        assertEquals(409, responseStatus.get(), "Should return HTTP 409 Conflict");
        assertTrue(responseBuffer.toString().contains("ORGANIZATION_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("ORG-04: Submitting without CSRF token is intercepted and blocked with 403 Forbidden")
    void testOrg04_MissingCsrfBlocked() throws Exception {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        // Session has a token, but request header does NOT provide it
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, UUID.randomUUID().toString());

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()));
        CsrfFilter csrfFilter = new CsrfFilter();

        String body = "{\"name\":\"Hacked Org\",\"contactEmail\":\"hacked@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        csrfFilter.doFilter(req, resp, (request, response) -> {
            chainExecuted.set(true);
            try {
                servlet.doPost((HttpServletRequest) request, (HttpServletResponse) response);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        assertFalse(chainExecuted.get(), "FilterChain should NOT execute when CSRF is missing");
        assertEquals(403, responseStatus.get(), "CsrfFilter must return HTTP 403");
        assertTrue(responseBuffer.toString().contains("CSRF_TOKEN_INVALID"));
    }

    @Test
    @DisplayName("ORG-05: Non-admin accessing request detail of another user returns 403 Forbidden (IDOR Guard)")
    void testOrg05_IdorGuardBlocksUnauthorizedDetailAccess() throws Exception {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();

        // User A logged in
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(userA, PlatformRole.CUSTOMER, 1));

        OrganizationRequestDetailDto detailForOwnerOnly = new OrganizationRequestDetailDto(
                orgId, "User B Org", "owner@userb.vn", "0901234567", "Desc",
                OrganizationStatus.PENDING_APPROVAL, userB, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto getRequestDetail(UUID actorId, boolean isAdmin, UUID targetId) {
                if (!isAdmin && !userB.equals(actorId)) {
                    throw new SecurityException("FORBIDDEN: Bạn không có quyền truy cập hồ sơ này");
                }
                return detailForOwnerOnly;
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);

        // 1. User A tries to view User B's request -> 403
        HttpServletRequest reqUserA = createMockRequest("GET", "/api/organization-requests/" + orgId, true, null);
        HttpServletResponse respUserA = createMockResponse();

        servlet.doGet(reqUserA, respUserA);

        assertEquals(403, responseStatus.get(), "IDOR access attempt must be rejected with 403");
        assertTrue(responseBuffer.toString().contains("FORBIDDEN"));

        // 2. Admin tries to view User B's request -> 200 OK
        responseBuffer = new StringWriter();
        responseStatus.set(200);
        UUID adminId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        HttpServletRequest reqAdmin = createMockRequest("GET", "/api/organization-requests/" + orgId, true, null);
        HttpServletResponse respAdmin = createMockResponse();

        servlet.doGet(reqAdmin, respAdmin);

        assertEquals(200, responseStatus.get(), "Admin must be permitted to view any request");
        assertTrue(responseBuffer.toString().contains("User B Org"));
    }
}
