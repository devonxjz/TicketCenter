package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.refund.dao.AdminRefundStore;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.service.*;
import vn.ticketscenter.identity.config.IdentityServices;
import vn.ticketscenter.identity.dao.UserRepository;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.SessionValidator;

import static org.junit.jupiter.api.Assertions.*;

class AdminRefundRequestsServletTest {
    final UUID refundId = UUID.randomUUID(), key = UUID.randomUUID();
    final UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), PlatformRole.ADMIN, 0);
    final UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 0);
    final Map<String, Object> attributes = new HashMap<>(), contextAttributes = new HashMap<>(), sessionAttributes = new HashMap<>();
    final Map<String, String> headers = new HashMap<>(), responseHeaders = new HashMap<>();
    final StringWriter output = new StringWriter();
    final FakeStore store = new FakeStore(refundId);
    String body = "{\"decision\":\"APPROVE\",\"note\":\"ok\"}", path = "/api/admin/refund-requests", forwarded, method = "GET";
    int status = 200;
    boolean sessionValid = true;

    final ServletContext context = proxy(ServletContext.class, (name, args) -> switch (name) {
        case "getAttribute" -> contextAttributes.get(args[0]);
        case "setAttribute" -> { contextAttributes.put((String) args[0], args[1]); yield null; }
        default -> null;
    });

    AdminRefundRequestsServletTest() {
        contextAttributes.put(IdentityServices.SESSION_VALIDATOR_ATTRIBUTE, new SessionValidator(new TransactionManager(Map.of()), new UserRepository()) {
            @Override public boolean isSessionValid(UUID id, int version) { return sessionValid; }
        });
        contextAttributes.put(AdminRefundRuntime.SERVICE_ATTRIBUTE, new AdminRefundDecisionServiceImpl(store, (a, b, c) -> {}));
    }

    void loginAdmin() {
        attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, admin);
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, "csrf-valid");
        headers.put("X-CSRF-Token", "csrf-valid");
        headers.put("Idempotency-Key", key.toString());
    }

    void loginCustomer() {
        attributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, customer);
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, "csrf-valid");
        headers.put("X-CSRF-Token", "csrf-valid");
        headers.put("Idempotency-Key", key.toString());
    }

    HttpServletRequest request() {
        return proxy(HttpServletRequest.class, (n, a) -> switch (n) {
            case "getServletContext" -> context;
            case "getAttribute" -> attributes.get(a[0]);
            case "setAttribute" -> { attributes.put((String) a[0], a[1]); yield null; }
            case "removeAttribute" -> { attributes.remove(a[0]); yield null; }
            case "getSession" -> proxy(HttpSession.class, (m, b) -> m.equals("getAttribute") ? sessionAttributes.get(b[0]) : null);
            case "getHeader" -> headers.get(a[0]);
            case "getContentType" -> "application/json;charset=UTF-8";
            case "getReader" -> new BufferedReader(new StringReader(body));
            case "getRequestURI" -> "/tc" + path;
            case "getContextPath" -> "/tc";
            case "getMethod" -> method;
            case "getParameter" -> null;
            case "getRequestDispatcher" -> dispatcher((String) a[0]);
            default -> null;
        });
    }

    HttpServletResponse response() {
        return proxy(HttpServletResponse.class, (n, a) -> switch (n) {
            case "setStatus" -> { status = (int) a[0]; yield null; }
            case "setHeader" -> { responseHeaders.put((String) a[0], (String) a[1]); yield null; }
            case "getWriter" -> new PrintWriter(output);
            default -> null;
        });
    }

    RequestDispatcher dispatcher(String target) {
        return proxy(RequestDispatcher.class, (n, a) -> {
            if (n.equals("forward")) forwarded = target;
            return null;
        });
    }

    AdminRefundRequestsServlet servlet() { return new AdminRefundRequestsServlet(); }

    @Test void unauthenticatedReturns401() throws Exception {
        servlet().doGet(request(), response());
        assertEquals(401, status);
        method = "POST";
        path = "/api/admin/refund-requests/" + refundId + "/decision";
        status = 200;
        servlet().doPost(request(), response());
        assertEquals(401, status);
    }

    @Test void nonAdminReturns403() throws Exception {
        loginCustomer();
        servlet().doGet(request(), response());
        assertEquals(403, status);
        method = "POST";
        path = "/api/admin/refund-requests/" + refundId + "/decision";
        status = 200;
        servlet().doPost(request(), response());
        assertEquals(403, status);
    }

    @Test void adminListApiReturns200AndNoStore() throws Exception {
        loginAdmin();
        servlet().doGet(request(), response());
        assertEquals(200, status);
        assertEquals("no-store", responseHeaders.get("Cache-Control"));
        assertTrue(output.toString().contains("\"data\":"));
    }

    @Test void adminListHtmlForwardsToJsp() throws Exception {
        loginAdmin();
        path = "/admin/refund-requests";
        servlet().doGet(request(), response());
        assertEquals("/WEB-INF/views/admin/refunds/list.jsp", forwarded);
        assertNotNull(attributes.get("refundPageJson"));
    }

    @Test void adminDetailApiReturns200() throws Exception {
        loginAdmin();
        path = "/api/admin/refund-requests/" + refundId;
        servlet().doGet(request(), response());
        assertEquals(200, status);
        assertTrue(output.toString().contains("\"data\":"));
    }

    @Test void adminDetailHtmlForwardsToJsp() throws Exception {
        loginAdmin();
        path = "/admin/refund-requests/" + refundId;
        servlet().doGet(request(), response());
        assertEquals("/WEB-INF/views/admin/refunds/review.jsp", forwarded);
        assertEquals(refundId.toString(), attributes.get("refundId"));
    }

    @Test void adminDecisionPostSucceeds() throws Exception {
        loginAdmin();
        method = "POST";
        path = "/api/admin/refund-requests/" + refundId + "/decision";
        servlet().doPost(request(), response());
        assertEquals(200, status);
        assertEquals(1, store.decideCount);
        assertTrue(output.toString().contains("\"APPROVED\""));
    }

    @Test void adminRetryPostSucceeds() throws Exception {
        loginAdmin();
        method = "POST";
        path = "/api/admin/refund-requests/" + refundId + "/retry";
        body = "{}";
        servlet().doPost(request(), response());
        assertEquals(200, status);
        assertEquals(1, store.retryCount);
        assertTrue(output.toString().contains("\"PROCESSING\""));
    }

    @Test void postInvalidMethodReturns405() throws Exception {
        loginAdmin();
        method = "POST";
        path = "/api/admin/refund-requests";
        servlet().doPost(request(), response());
        assertEquals(405, status);
    }

    @Test void postInvalidPathTargetReturns404() throws Exception {
        loginAdmin();
        method = "POST";
        path = "/admin/refund-requests/" + refundId + "/decision";
        servlet().doPost(request(), response());
        assertEquals(404, status);
    }

    @Test void postPayloadTooLargeReturns413() throws Exception {
        loginAdmin();
        method = "POST";
        path = "/api/admin/refund-requests/" + refundId + "/decision";
        body = "{\"decision\":\"APPROVE\",\"note\":\"" + "x".repeat(8200) + "\"}";
        servlet().doPost(request(), response());
        assertEquals(413, status);
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, RefundRequestServletsTest.Bi work) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> work.run(m.getName(), a == null ? new Object[0] : a));
    }

    static class FakeStore implements AdminRefundStore {
        final UUID refundId;
        int decideCount, retryCount;

        FakeStore(UUID refundId) { this.refundId = refundId; }

        @Override public void requireAdmin(UserPrincipal actor) {}

        @Override public AdminRefundPageDto list(String status, int page, int size) {
            return new AdminRefundPageDto(List.of(), 0, page, size);
        }

        @Override public AdminRefundDetailDto detail(UUID id) {
            var summary = new AdminRefundSummaryDto(
                    refundId, UUID.randomUUID(), UUID.randomUUID(), "Buyer", "buyer@example.com",
                    "Event", "100000", 1, "Reason", "REQUESTED", Instant.EPOCH, null, true, false);
            return new AdminRefundDetailDto(summary, null, null, null, null, List.of(), List.of());
        }

        @Override public AdminRefundMutationDto decide(
                UserPrincipal actor, UUID id, AdminRefundDecisionRequestDto request, UUID key) {
            decideCount++;
            return new AdminRefundMutationDto(refundId, "APPROVED", "APPROVED", UUID.randomUUID(), UUID.randomUUID(), List.of());
        }

        @Override public AdminRefundMutationDto retry(UserPrincipal actor, UUID id, UUID key) {
            retryCount++;
            return new AdminRefundMutationDto(refundId, "PROCESSING", "PROCESSING", UUID.randomUUID(), UUID.randomUUID(), List.of());
        }
    }
}
