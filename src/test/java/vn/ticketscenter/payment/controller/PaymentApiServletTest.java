package vn.ticketscenter.payment.controller;

import vn.ticketscenter.config.web.TestServlets;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.controller.OrderApiServlet;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import vn.ticketscenter.payment.service.PaymentException;
import vn.ticketscenter.payment.service.PaymentService;
import vn.ticketscenter.payment.service.PaymentServiceImpl;

import java.io.*;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PaymentApiServletTest {

    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String CSRF_TOKEN = "valid-csrf-token-12345";

    private Map<String, Object> sessionAttributes;
    private Map<String, Object> requestAttributes;
    private Map<String, List<String>> requestHeaders;
    private StringWriter responseOutput;
    private AtomicInteger responseStatus;
    private StubPaymentService paymentService;
    private OrderApiServlet servlet;

    private static class StubPaymentService extends PaymentServiceImpl {
        PaymentInitiationResult resultToReturn;
        PaymentException exceptionToThrow;
        UUID capturedActorId;
        UUID capturedOrderId;
        UUID capturedKey;

        public StubPaymentService() {
            super();
        }

        @Override
        public PaymentInitiationResult initiate(UUID actorId, UUID orderId, UUID key) {
            this.capturedActorId = actorId;
            this.capturedOrderId = orderId;
            this.capturedKey = key;
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            return resultToReturn;
        }

        vn.ticketscenter.payment.dto.PaymentStatusResult statusToReturn;

        @Override
        public vn.ticketscenter.payment.dto.PaymentStatusResult getOrderPaymentStatus(UUID actorId, UUID orderId) {
            if (exceptionToThrow != null) {
                throw exceptionToThrow;
            }
            if (statusToReturn != null) {
                return statusToReturn;
            }
            throw PaymentException.orderNotFound();
        }
    }

    @BeforeEach
    void setUp() {
        sessionAttributes = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        responseOutput = new StringWriter();
        responseStatus = new AtomicInteger(200);

        sessionAttributes.put(SecurityContext.SESSION_USER_ATTRIBUTE, new UserPrincipal(ACTOR_ID, PlatformRole.CUSTOMER, 1));
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, CSRF_TOKEN);
        requestHeaders.put("X-CSRF-Token", List.of(CSRF_TOKEN));
        requestHeaders.put("Idempotency-Key", List.of(IDEMPOTENCY_KEY.toString()));

        paymentService = new StubPaymentService();
        servlet = TestServlets.init(new OrderApiServlet(), null, paymentService);
    }

    @Test
    void unauthenticatedRequestReturns401() throws Exception {
        sessionAttributes.remove(SecurityContext.SESSION_USER_ATTRIBUTE);
        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(401, responseStatus.get());
        assertTrue(responseOutput.toString().contains("UNAUTHENTICATED"));
    }

    @Test
    void missingOrInvalidCsrfReturns403() throws Exception {
        requestHeaders.put("X-CSRF-Token", List.of("wrong-csrf-token"));
        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(403, responseStatus.get());
        assertTrue(responseOutput.toString().contains("CSRF_TOKEN_INVALID"));
    }

    @Test
    void invalidContentTypeReturns415() throws Exception {
        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}", "text/plain");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(415, responseStatus.get());
        assertTrue(responseOutput.toString().contains("CONTENT_TYPE_INVALID"));
    }

    @Test
    void missingIdempotencyKeyReturns400() throws Exception {
        requestHeaders.remove("Idempotency-Key");
        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(400, responseStatus.get());
        assertTrue(responseOutput.toString().contains("INVALID_REQUEST"));
    }

    @Test
    void duplicateIdempotencyKeyReturns400() throws Exception {
        requestHeaders.put("Idempotency-Key", List.of(IDEMPOTENCY_KEY.toString(), IDEMPOTENCY_KEY.toString()));
        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(400, responseStatus.get());
        assertTrue(responseOutput.toString().contains("INVALID_REQUEST"));
    }

    @Test
    void invalidOrderIdReturns400() throws Exception {
        HttpServletRequest req = mockRequest("POST", "/api/orders/invalid-uuid/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(400, responseStatus.get());
        assertTrue(responseOutput.toString().contains("INVALID_REQUEST"));
    }

    @Test
    void successfullyInitiatesPaymentReturns200AndRedirectUrl() throws Exception {
        UUID pid = UUID.randomUUID();
        paymentService.resultToReturn = PaymentInitiationResult.redirect(
                pid,
                "TXN-123",
                new BigDecimal("500000"),
                "https://pay.payos.vn/web/0123456789abcdef"
        );

        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertEquals("https://pay.payos.vn/web/0123456789abcdef", json.getString("redirectUrl"));
        assertEquals("TXN-123", json.getString("txnRef"));
        assertEquals(pid.toString(), json.getString("paymentId"));
        assertFalse(json.getBoolean("zeroOrder"));

        // Xác nhận truyền đúng tham số vào service
        assertEquals(ACTOR_ID, paymentService.capturedActorId);
        assertEquals(ORDER_ID, paymentService.capturedOrderId);
        assertEquals(IDEMPOTENCY_KEY, paymentService.capturedKey);
    }

    @Test
    void zeroOrderReturns200WithZeroOrderReceipt() throws Exception {
        paymentService.resultToReturn = PaymentInitiationResult.zero(ORDER_ID);

        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertTrue(json.getBoolean("zeroOrder"));
        assertEquals("PAID", json.getString("status"));
        assertTrue(json.isNull("redirectUrl"));
    }

    @Test
    void idempotencyConflictExceptionReturns409() throws Exception {
        paymentService.exceptionToThrow = PaymentException.idempotencyConflict();

        HttpServletRequest req = mockRequest("POST", "/api/orders/" + ORDER_ID + "/payments", "{}");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(409, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertEquals("IDEMPOTENCY_CONFLICT", json.getJSONObject("error").getString("code"));
    }

    @Test
    void getOrderPaymentStatusReturns200WithStatusDetails() throws Exception {
        UUID pid = UUID.randomUUID();
        paymentService.statusToReturn = new vn.ticketscenter.payment.dto.PaymentStatusResult(
                ORDER_ID, "PAID", Optional.of(pid), Optional.of("CAPTURED"),
                new BigDecimal("250000"), "VND", Optional.of(java.time.Instant.now())
        );

        HttpServletRequest req = mockRequest("GET", "/api/orders/" + ORDER_ID + "/payment-status", "");
        HttpServletResponse res = mockResponse();

        invokeServlet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertEquals("PAID", json.getString("orderStatus"));
        assertEquals("CAPTURED", json.getString("paymentStatus"));
        assertEquals("250000", json.getString("amount"));
    }

    private void invokeServlet(HttpServletRequest req, HttpServletResponse res) throws Exception {
        var serviceMethod = OrderApiServlet.class.getDeclaredMethod("service", HttpServletRequest.class, HttpServletResponse.class);
        serviceMethod.setAccessible(true);
        serviceMethod.invoke(servlet, req, res);
    }

    private HttpServletRequest mockRequest(String method, String uri, String body) {
        return mockRequest(method, uri, body, "application/json");
    }

    private HttpServletRequest mockRequest(String method, String uri, String body, String contentType) {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getContentType" -> contentType;
                    case "getContentLengthLong" -> (long) bytes.length;
                    case "getContentLength" -> bytes.length;
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        requestAttributes.remove((String) args[0]);
                        yield null;
                    }
                    case "getHeader" -> {
                        List<String> vals = requestHeaders.get((String) args[0]);
                        yield (vals != null && !vals.isEmpty()) ? vals.get(0) : null;
                    }
                    case "getHeaders" -> {
                        List<String> vals = requestHeaders.get((String) args[0]);
                        yield vals != null ? Collections.enumeration(vals) : Collections.emptyEnumeration();
                    }
                    case "getSession" -> {
                        boolean create = args.length == 0 || (boolean) args[0];
                        yield mockSession(create);
                    }
                    case "getInputStream" -> createServletInputStream(bytes);
                    case "getRemoteAddr" -> "127.0.0.1";
                    default -> null;
                }
        );
    }

    private HttpSession mockSession(boolean create) {
        if (!create && sessionAttributes.isEmpty()) return null;
        return (HttpSession) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpSession.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getAttribute" -> sessionAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        sessionAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "removeAttribute" -> {
                        sessionAttributes.remove((String) args[0]);
                        yield null;
                    }
                    default -> null;
                }
        );
    }

    private HttpServletResponse mockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus" -> {
                        responseStatus.set((int) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "getWriter" -> new PrintWriter(responseOutput);
                    case "setCharacterEncoding", "setContentType", "setHeader" -> null;
                    default -> null;
                }
        );
    }

    private static ServletInputStream createServletInputStream(byte[] data) {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        return new ServletInputStream() {
            @Override public boolean isFinished() { return bais.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener readListener) { }
            @Override public int read() { return bais.read(); }
            @Override public int read(byte[] b, int off, int len) { return bais.read(b, off, len); }
        };
    }
}
