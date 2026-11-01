package vn.ticketscenter.payment.controller;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.dao.CompensationRetryRepository;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.UUID;
import vn.ticketscenter.payment.dao.CompensationRetryStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminCompensationRetryServletTest {
    private static final String CSRF = "csrf-token";
    private static final UUID PAYMENT = UUID.randomUUID();
    private static final UUID COMPENSATION = UUID.randomUUID();
    private final UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), PlatformRole.ADMIN, 1, null, "admin@example.com");
    private final UserPrincipal customer = new UserPrincipal(UUID.randomUUID(), PlatformRole.CUSTOMER, 1, null, "buyer@example.com");

    @Test
    void adminRetryReturnsTheNewPendingCompensation() throws Exception {
        Result result = call(admin, CSRF, "/api/admin/payments/" + PAYMENT + "/compensation/retry", (actor, payment) -> COMPENSATION);
        assertEquals(200, result.status);
        assertTrue(result.body.toString().contains("\"compensationId\":\"" + COMPENSATION + "\""));
        assertTrue(result.body.toString().contains("\"status\":\"PENDING\""));
    }

    @Test
    void nonAdminMissingCsrfAndUnknownRoutesAreRejected() throws Exception {
        String path = "/api/admin/payments/" + PAYMENT + "/compensation/retry";
        assertEquals(403, call(customer, CSRF, path, (actor, payment) -> COMPENSATION).status);
        assertEquals(403, call(admin, null, path, (actor, payment) -> COMPENSATION).status);
        assertEquals(401, call(null, CSRF, path, (actor, payment) -> COMPENSATION).status);
        assertEquals(404, call(admin, CSRF, "/api/admin/payments/" + PAYMENT, (actor, payment) -> COMPENSATION).status);
    }

    @Test
    void procedureErrorsMapToHttpStatuses() throws Exception {
        String path = "/api/admin/payments/" + PAYMENT + "/compensation/retry";
        assertEquals(409, call(admin, CSRF, path, (actor, payment) -> { throw failure(52812); }).status);
        assertEquals(404, call(admin, CSRF, path, (actor, payment) -> { throw failure(52811); }).status);
        assertEquals(403, call(admin, CSRF, path, (actor, payment) -> { throw failure(52810); }).status);
        Result unknown = call(admin, CSRF, path, (actor, payment) -> { throw new IllegalStateException("secret detail"); });
        assertEquals(503, unknown.status);
        assertTrue(!unknown.body.toString().contains("secret detail"));
    }

    private static RuntimeException failure(int code) {
        return CompensationRetryRepository.translate(new IllegalStateException(new SQLException("failed", "S0001", code)));
    }

    private Result call(UserPrincipal actor, String csrfHeader, String uri, CompensationRetryStore retry) throws Exception {
        Result result = new Result();
        HttpSession session = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class},
                (proxy, method, args) -> "getAttribute".equals(method.getName())
                        && SecurityContext.CSRF_TOKEN_ATTRIBUTE.equals(args[0]) ? CSRF : null);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getAttribute" -> SecurityContext.REQUEST_USER_ATTRIBUTE.equals(args[0]) ? actor : null;
                    case "getHeader" -> SecurityContext.CSRF_HEADER_NAME.equalsIgnoreCase((String) args[0]) ? csrfHeader : null;
                    case "getSession" -> session;
                    default -> null;
                });
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(),
                new Class<?>[]{HttpServletResponse.class}, (proxy, method, args) -> {
                    if ("setStatus".equals(method.getName())) result.status = (Integer) args[0];
                    if ("getWriter".equals(method.getName())) return new PrintWriter(result.body, true);
                    return null;
                });
        TestServlets.init(new AdminCompensationRetryServlet() { void run() throws Exception { doPost(request, response); } }, retry).run();
        return result;
    }

    private static final class Result {
        int status = 200;
        final StringWriter body = new StringWriter();
    }
}
