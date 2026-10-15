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
import vn.ticketscenter.identity.dto.OrganizationRequestSummaryDto;
import vn.ticketscenter.identity.model.OrganizationStatus;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.repository.OrganizationRepository;
import vn.ticketscenter.identity.repository.UserRepository;
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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class OrganizationRequestServletTest {

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private StringWriter responseBuffer;
    private AtomicInteger responseStatus;

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        responseBuffer = new StringWriter();
        responseStatus = new AtomicInteger(200);
    }

    private OrganizationRequestService createDummyService() {
        return new OrganizationRequestService(
                new TransactionManager(Map.of()),
                new OrganizationRepository(),
                new UserRepository()
        );
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
                    case "getId" -> "mock-session-id";
                    default -> null;
                }
        );
    }

    private HttpServletRequest createMockRequest(String method, String uri, boolean hasSession, String jsonBody) {
        HttpSession session = hasSession ? createMockSession() : null;
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
    @DisplayName("POST /api/organization-requests returns 401 when unauthenticated")
    void testPostUnauthenticated() throws IOException {
        OrganizationRequestServlet servlet = new OrganizationRequestServlet(createDummyService());
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", false, "{}");
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/me/organization-requests returns 401 when unauthenticated")
    void testGetMyRequestsUnauthenticated() throws IOException {
        OrganizationRequestServlet servlet = new OrganizationRequestServlet(createDummyService());
        HttpServletRequest req = createMockRequest("GET", "/api/me/organization-requests", false, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 400 for invalid JSON payload")
    void testPostInvalidJson() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(createDummyService());
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, "invalid json");
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("INVALID_PAYLOAD"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 201 on valid submission")
    void testPostValidSubmission() throws IOException {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestDetailDto mockDetail = new OrganizationRequestDetailDto(
                orgId, "Acme Events", "contact@acme.vn", "0901234567", "Desc",
                OrganizationStatus.PENDING_APPROVAL, actorId, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                assertEquals(actorId, id);
                assertEquals("Acme Events", dto.name());
                assertEquals("contact@acme.vn", dto.contactEmail());
                return mockDetail;
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        String body = "{\"name\":\"Acme Events\",\"contactEmail\":\"contact@acme.vn\",\"contactPhone\":\"0901234567\",\"description\":\"Desc\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(201, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("Acme Events"));
        assertTrue(responseBuffer.toString().contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 400 on validation failure")
    void testPostValidationFailure() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                throw new IllegalArgumentException("Organization name must not be blank.");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        String body = "{\"name\":\"\",\"contactEmail\":\"test@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 409 when pending request exists")
    void testPostConflictPendingExists() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                throw new IdentityException(IdentityError.ORGANIZATION_REQUEST_EXISTS,
                        "Bạn đang có một yêu cầu tạo tổ chức đang chờ xét duyệt");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        String body = "{\"name\":\"Duplicate Org\",\"contactEmail\":\"dup@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(409, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("ORGANIZATION_REQUEST_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 409 on idempotency conflict")
    void testPostIdempotencyConflict() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                throw new IdentityException(IdentityError.IDEMPOTENCY_CONFLICT,
                        "Idempotency-Key đã được sử dụng với nội dung khác");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        String body = "{\"name\":\"Conflict Org\",\"contactEmail\":\"conf@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        requestHeaders.put("Idempotency-Key", "conflict-key-123");
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(409, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/organization-requests returns 403 when user is suspended")
    void testPostUserSuspended() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto submitRequest(UUID id, OrganizationRequestCreateDto dto, String key) {
                throw new IdentityException(IdentityError.USER_SUSPENDED,
                        "Tài khoản của bạn hiện không thể gửi yêu cầu");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        String body = "{\"name\":\"Suspended Org\",\"contactEmail\":\"susp@test.vn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/organization-requests", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(403, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("USER_SUSPENDED"));
    }

    @Test
    @DisplayName("GET /api/me/organization-requests returns 200 with list")
    void testGetMyRequestsSuccess() throws IOException {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestSummaryDto summary = new OrganizationRequestSummaryDto(
                orgId, "My Org", "org@test.vn", OrganizationStatus.PENDING_APPROVAL, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public List<OrganizationRequestSummaryDto> getMyRequests(UUID id) {
                assertEquals(actorId, id);
                return List.of(summary);
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/me/organization-requests", true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("My Org"));
        assertTrue(responseBuffer.toString().contains("PENDING_APPROVAL"));
    }

    @Test
    @DisplayName("GET /api/organization-requests/{id} returns 200 for owner")
    void testGetDetailOwnerSuccess() throws IOException {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestDetailDto detail = new OrganizationRequestDetailDto(
                orgId, "Owner Org", "owner@test.vn", "0901234567", "Desc",
                OrganizationStatus.PENDING_APPROVAL, actorId, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto getRequestDetail(UUID id, boolean admin, UUID targetOrgId) {
                assertEquals(actorId, id);
                assertFalse(admin);
                assertEquals(orgId, targetOrgId);
                return detail;
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/organization-requests/" + orgId, true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("Owner Org"));
    }

    @Test
    @DisplayName("GET /api/organization-requests/{id} returns 403 on IDOR unauthorized access")
    void testGetDetailIdorForbidden() throws IOException {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto getRequestDetail(UUID id, boolean admin, UUID targetOrgId) {
                throw new SecurityException("FORBIDDEN: Bạn không có quyền truy cập hồ sơ này");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/organization-requests/" + orgId, true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(403, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/organization-requests/{id} returns 404 when request not found")
    void testGetDetailNotFound() throws IOException {
        UUID actorId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto getRequestDetail(UUID id, boolean admin, UUID targetOrgId) {
                throw new NoSuchElementException("ORGANIZATION_NOT_FOUND");
            }
        };

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/organization-requests/" + orgId, true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(404, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("ORGANIZATION_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /api/organization-requests/invalid-uuid returns 400")
    void testGetDetailInvalidUuid() throws IOException {
        UUID actorId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));

        OrganizationRequestServlet servlet = new OrganizationRequestServlet(createDummyService());
        HttpServletRequest req = createMockRequest("GET", "/api/organization-requests/not-a-uuid", true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("INVALID_ORGANIZATION_ID"));
    }

}
