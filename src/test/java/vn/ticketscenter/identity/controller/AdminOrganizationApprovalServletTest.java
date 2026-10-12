package vn.ticketscenter.identity.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AdminOrganizationApprovalServletTest {

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
                    case "getId" -> "test-session-id";
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
                    case "getParameter" -> {
                        String query = uri.contains("?") ? uri.substring(uri.indexOf("?") + 1) : "";
                        String paramName = (String) args[0];
                        yield Arrays.stream(query.split("&"))
                                .filter(s -> s.startsWith(paramName + "="))
                                .map(s -> s.substring(paramName.length() + 1))
                                .findFirst()
                                .orElse(null);
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
    @DisplayName("GET /api/admin/organization-requests returns 401 when unauthenticated")
    void testGetRequestsUnauthenticated() throws IOException {
        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(createDummyService());
        HttpServletRequest req = createMockRequest("GET", "/api/admin/organization-requests", false, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(401, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("GET /api/admin/organization-requests returns 403 for Customer")
    void testGetRequestsCustomerForbidden() throws IOException {
        UUID customerId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(customerId, PlatformRole.CUSTOMER, 1));

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(createDummyService());
        HttpServletRequest req = createMockRequest("GET", "/api/admin/organization-requests", true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(403, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("GET /api/admin/organization-requests returns 200 OK with items, total, page, pageSize for Admin")
    void testGetRequestsAdminSuccess() throws IOException {
        UUID adminId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        UUID orgId = UUID.randomUUID();
        OrganizationRequestSummaryDto item = new OrganizationRequestSummaryDto(
                orgId, "Test Org", "contact@test.vn", OrganizationStatus.PENDING_APPROVAL, Instant.now(), null, "contact@test.vn"
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public List<OrganizationRequestSummaryDto> getRequestsForAdmin(UUID aid, OrganizationStatus status, int page, int pageSize) {
                assertEquals(adminId, aid);
                assertEquals(1, page);
                assertEquals(20, pageSize);
                return List.of(item);
            }

            @Override
            public long countRequestsForAdmin(UUID aid, OrganizationStatus status) {
                assertEquals(adminId, aid);
                return 1L;
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/admin/organization-requests", true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        String body = responseBuffer.toString();
        assertTrue(body.contains("\"items\":"));
        assertTrue(body.contains("\"total\":1"));
        assertTrue(body.contains("\"page\":1"));
        assertTrue(body.contains("\"pageSize\":20"));
        assertTrue(body.contains("Test Org"));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/approve returns 403 for Customer")
    void testPostApproveCustomerForbidden() throws IOException {
        UUID customerId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(customerId, PlatformRole.CUSTOMER, 1));

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(createDummyService());
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/approve", true, "{}");
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(403, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("FORBIDDEN"));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/approve returns 200 OK with success and status APPROVED for Admin with valid policy")
    void testPostApproveAdminValidPolicy() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestDetailDto detail = new OrganizationRequestDetailDto(
                orgId, "Approved Org", "org@test.vn", "0901234567", "Desc",
                OrganizationStatus.APPROVED, adminId, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto approveRequest(UUID aid, UUID oid, BigDecimal ratePercent, Long fixedFee, Instant effectiveFrom) {
                assertEquals(adminId, aid);
                assertEquals(orgId, oid);
                assertEquals(new BigDecimal("5.0"), ratePercent);
                assertEquals(10000L, fixedFee);
                return detail;
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        String body = "{\"ratePercent\":\"5.0\",\"fixedFee\":\"10000\",\"effectiveDate\":\"2026-11-01\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/approve", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        String res = responseBuffer.toString();
        assertTrue(res.contains("\"success\":true"));
        assertTrue(res.contains("\"status\":\"APPROVED\""));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/approve returns 400 VALIDATION_ERROR for Admin with invalid policy")
    void testPostApproveAdminInvalidPolicy() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(createDummyService());
        String body = "{\"ratePercent\":\"-5.0\",\"fixedFee\":\"10000\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/approve", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/approve returns 409 CONFLICT when org is already processed")
    void testPostApproveAlreadyProcessedConflict() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto approveRequest(UUID aid, UUID oid, BigDecimal ratePercent, Long fixedFee, Instant effectiveFrom) {
                throw new IllegalStateException("CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL");
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        String body = "{\"ratePercent\":\"5.0\",\"fixedFee\":\"10000\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/approve", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(409, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("CONFLICT"));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/reject returns 200 OK with success and status REJECTED for Admin with valid reason")
    void testPostRejectAdminValidReason() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestDetailDto detail = new OrganizationRequestDetailDto(
                orgId, "Rejected Org", "org@test.vn", "0901234567", "Desc",
                OrganizationStatus.REJECTED, adminId, Instant.now(), "Hồ sơ không đáp ứng điều kiện quy định"
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto rejectRequest(UUID aid, UUID oid, String reason) {
                assertEquals(adminId, aid);
                assertEquals(orgId, oid);
                assertEquals("Hồ sơ không đáp ứng điều kiện quy định", reason);
                return detail;
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        String body = "{\"reason\":\"Hồ sơ không đáp ứng điều kiện quy định\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/reject", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        String res = responseBuffer.toString();
        assertTrue(res.contains("\"success\":true"));
        assertTrue(res.contains("\"status\":\"REJECTED\""));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/reject returns 400 VALIDATION_ERROR for empty reason or < 10 characters")
    void testPostRejectAdminEmptyOrShortReason() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(createDummyService());
        String body = "{\"reason\":\"Quá ngắn\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/reject", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(400, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/reject returns 409 CONFLICT when org is already processed")
    void testPostRejectAlreadyProcessedConflict() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto rejectRequest(UUID aid, UUID oid, String reason) {
                throw new IllegalStateException("CONFLICT: Yêu cầu không ở trạng thái PENDING_APPROVAL");
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        String body = "{\"reason\":\"Lý do từ chối hợp lệ nhưng hồ sơ đã xử lý\"}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/reject", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(409, responseStatus.get());
        assertTrue(responseBuffer.toString().contains("CONFLICT"));
    }

    @Test
    @DisplayName("GET /api/admin/organization-requests/{id} returns 200 OK with detail for Admin")
    void testGetDetailAdminSuccess() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestDetailDto detail = new OrganizationRequestDetailDto(
                orgId, "Test Org Detail", "test@org.vn", "0912345678", "Desc",
                OrganizationStatus.PENDING_APPROVAL, adminId, Instant.now(), null
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto getRequestDetail(UUID aid, boolean isAdmin, UUID oid) {
                assertEquals(adminId, aid);
                assertTrue(isAdmin);
                assertEquals(orgId, oid);
                return detail;
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        HttpServletRequest req = createMockRequest("GET", "/api/admin/organization-requests/" + orgId, true, null);
        HttpServletResponse resp = createMockResponse();

        servlet.doGet(req, resp);

        assertEquals(200, responseStatus.get());
        String res = responseBuffer.toString();
        assertTrue(res.contains("Test Org Detail"));
        assertTrue(res.contains(orgId.toString()));
    }

    @Test
    @DisplayName("POST /api/admin/organization-requests/{id}/approve with effectiveTo and initialPolicy object")
    void testPostApproveWithInitialPolicyObject() throws IOException {
        UUID adminId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID ruleId = UUID.randomUUID();
        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(adminId, PlatformRole.ADMIN, 1));

        OrganizationRequestDetailDto detail = new OrganizationRequestDetailDto(
                orgId, "Approved Org Policy", "org@test.vn", "0901234567", "Desc",
                OrganizationStatus.APPROVED, adminId, Instant.now(), null, ruleId
        );

        OrganizationRequestService mockService = new OrganizationRequestService(
                new TransactionManager(Map.of()), new OrganizationRepository(), new UserRepository()) {
            @Override
            public OrganizationRequestDetailDto approveRequest(UUID aid, UUID oid, BigDecimal ratePercent, Long fixedFee, Instant effectiveFrom, Instant effectiveTo) {
                assertEquals(adminId, aid);
                assertEquals(orgId, oid);
                assertEquals(new BigDecimal("7.5"), ratePercent);
                assertEquals(15000L, fixedFee);
                assertNotNull(effectiveFrom);
                assertNotNull(effectiveTo);
                return detail;
            }
        };

        AdminOrganizationApprovalServlet servlet = new AdminOrganizationApprovalServlet(mockService);
        String body = "{\"initialPolicy\":{\"ratePercent\":\"7.5\",\"fixedFee\":\"15000\",\"effectiveFrom\":\"2026-11-01\",\"effectiveTo\":\"2027-11-01\"}}";
        HttpServletRequest req = createMockRequest("POST", "/api/admin/organization-requests/" + orgId + "/approve", true, body);
        HttpServletResponse resp = createMockResponse();

        servlet.doPost(req, resp);

        assertEquals(200, responseStatus.get());
        String res = responseBuffer.toString();
        assertTrue(res.contains("\"success\":true"));
        assertTrue(res.contains("\"status\":\"APPROVED\""));
        assertTrue(res.contains("\"assignedManagerId\""));
        assertTrue(res.contains(ruleId.toString()));
    }
}
