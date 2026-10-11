package vn.ticketscenter.payment.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.dto.VnpayIpnResult;
import vn.ticketscenter.payment.dto.VnpayReturnResult;
import vn.ticketscenter.payment.service.PaymentService;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class VnPayCallbackServletTest {

    private Map<String, String> requestParams;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private StringWriter responseOutput;
    private AtomicInteger responseStatus;
    private String forwardedPath;
    private StubPaymentService paymentService;
    private VnPayCallbackServlet servlet;

    private static class StubPaymentService extends PaymentService {
        VnpayIpnResult ipnResultToReturn = VnpayIpnResult.success();
        VnpayReturnResult returnResultToReturn = new VnpayReturnResult(
                true, true, "00", "TXN-1", Optional.of(UUID.randomUUID()), new BigDecimal("100000"), "Success"
        );
        Map<String, String> capturedIpnParams;
        Map<String, String> capturedReturnParams;

        public StubPaymentService() {
            super();
        }

        @Override
        public VnpayIpnResult processIpn(Map<String, String> queryParams) {
            this.capturedIpnParams = queryParams;
            return ipnResultToReturn;
        }

        @Override
        public VnpayReturnResult processReturn(Map<String, String> queryParams) {
            this.capturedReturnParams = queryParams;
            return returnResultToReturn;
        }
    }

    @BeforeEach
    void setUp() {
        requestParams = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        responseOutput = new StringWriter();
        responseStatus = new AtomicInteger(200);
        forwardedPath = null;

        paymentService = new StubPaymentService();
        servlet = new VnPayCallbackServlet(paymentService);
    }

    @Test
    void ipnEndpointReturnsProtocolJsonResponse() throws Exception {
        requestParams.put("vnp_TxnRef", "TXN-1234");
        requestParams.put("vnp_Amount", "10000000");
        requestParams.put("vnp_SecureHash", "HASH123");

        HttpServletRequest req = mockRequest("GET", "/api/payments/vnpay/ipn");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertEquals("00", json.getString("RspCode"));
        assertEquals("Confirm Success", json.getString("Message"));
        assertEquals("TXN-1234", paymentService.capturedIpnParams.get("vnp_TxnRef"));
    }

    @Test
    void returnEndpointWithJsonAcceptHeaderReturnsJson() throws Exception {
        requestHeaders.put("Accept", "application/json");
        requestParams.put("vnp_TxnRef", "TXN-1234");

        HttpServletRequest req = mockRequest("GET", "/api/payments/vnpay/return");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertTrue(json.getBoolean("success"));
        assertEquals("TXN-1", json.getString("txnRef"));
        assertNull(forwardedPath);
    }

    @Test
    void returnEndpointWithoutJsonAcceptHeaderForwardsToJsp() throws Exception {
        requestParams.put("vnp_TxnRef", "TXN-1234");

        HttpServletRequest req = mockRequest("GET", "/api/payments/vnpay/return");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals("/WEB-INF/views/payment/payment-result.jsp", forwardedPath);
        assertTrue((Boolean) requestAttributes.get("success"));
        assertEquals("TXN-1", requestAttributes.get("txnRef"));
        assertEquals(new BigDecimal("100000"), requestAttributes.get("amount"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        HttpServletRequest req = mockRequest("GET", "/api/payments/vnpay/unknown");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(404, responseStatus.get());
    }

    private HttpServletRequest mockRequest(String method, String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getParameter" -> requestParams.get((String) args[0]);
                    case "getParameterNames" -> Collections.enumeration(requestParams.keySet());
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getRequestDispatcher" -> {
                        forwardedPath = (String) args[0];
                        yield (RequestDispatcher) Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[]{RequestDispatcher.class},
                                (p, rm, rargs) -> null
                        );
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
                    case "setStatus", "sendError" -> {
                        responseStatus.set((int) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "getWriter" -> new PrintWriter(responseOutput);
                    case "setContentType", "setHeader", "setCharacterEncoding" -> null;
                    default -> null;
                }
        );
    }
}
