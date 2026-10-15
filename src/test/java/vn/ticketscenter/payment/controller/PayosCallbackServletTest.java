package vn.ticketscenter.payment.controller;

import jakarta.servlet.ReadListener;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.payment.dto.PaymentReturnResult;
import vn.ticketscenter.payment.dto.PayosWebhookResult;
import vn.ticketscenter.payment.service.PaymentService;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PayosCallbackServletTest {
    private Map<String, String> requestParams;
    private Map<String, Object> requestAttributes;
    private Map<String, String> requestHeaders;
    private String requestBody;
    private StringWriter responseOutput;
    private AtomicInteger responseStatus;
    private String forwardedPath;
    private StubPaymentService paymentService;
    private PayosCallbackServlet servlet;

    private static class StubPaymentService extends PaymentService {
        PayosWebhookResult webhookResult = PayosWebhookResult.confirmed();
        PaymentReturnResult returnResult = new PaymentReturnResult(
                true, true, "CAPTURED", "TXN-1", Optional.of(UUID.randomUUID()), new BigDecimal("100000"), "Success");
        String capturedWebhookBody;
        Map<String, String> capturedReturnParams;

        @Override public PayosWebhookResult processWebhook(String body) {
            capturedWebhookBody = body;
            return webhookResult;
        }
        @Override public PaymentReturnResult processReturn(Map<String, String> params) {
            capturedReturnParams = params;
            return returnResult;
        }
    }

    @BeforeEach
    void setUp() {
        requestParams = new HashMap<>();
        requestAttributes = new HashMap<>();
        requestHeaders = new HashMap<>();
        requestBody = "";
        responseOutput = new StringWriter();
        responseStatus = new AtomicInteger(200);
        forwardedPath = null;
        paymentService = new StubPaymentService();
        servlet = new PayosCallbackServlet(paymentService);
    }

    @Test
    void webhookPassesRawBodyToServiceAndAnswersJson() throws Exception {
        requestBody = "{\"code\":\"00\",\"data\":{\"orderCode\":123},\"signature\":\"abc\"}";
        servlet.doPost(mockRequest("POST", "/api/payments/payos/webhook"), mockResponse());

        assertEquals(200, responseStatus.get());
        assertEquals(requestBody, paymentService.capturedWebhookBody);
        JSONObject json = new JSONObject(responseOutput.toString());
        assertEquals(0, json.getInt("error"));
    }

    @Test
    void webhookWithInvalidSignatureAnswers400() throws Exception {
        paymentService.webhookResult = PayosWebhookResult.invalidSignature();
        servlet.doPost(mockRequest("POST", "/api/payments/payos/webhook"), mockResponse());
        assertEquals(400, responseStatus.get());
    }

    @Test
    void oversizedWebhookIsRejectedWithoutCallingService() throws Exception {
        requestBody = "x".repeat(70 * 1024);
        servlet.doPost(mockRequest("POST", "/api/payments/payos/webhook"), mockResponse());
        assertEquals(400, responseStatus.get());
        assertNull(paymentService.capturedWebhookBody);
    }

    @Test
    void returnWithJsonAcceptHeaderReturnsJson() throws Exception {
        requestHeaders.put("Accept", "application/json");
        requestParams.put("orderCode", "123");
        servlet.doGet(mockRequest("GET", "/api/payments/payos/return"), mockResponse());

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseOutput.toString());
        assertTrue(json.getBoolean("success"));
        assertEquals("TXN-1", json.getString("txnRef"));
        assertEquals("123", paymentService.capturedReturnParams.get("orderCode"));
        assertNull(forwardedPath);
    }

    @Test
    void cancelForwardsToResultPage() throws Exception {
        paymentService.returnResult = new PaymentReturnResult(
                true, false, "FAILED", "TXN-1", Optional.empty(), new BigDecimal("100000"), "Cancelled");
        requestParams.put("orderCode", "123");
        requestParams.put("cancel", "true");
        servlet.doGet(mockRequest("GET", "/api/payments/payos/cancel"), mockResponse());

        assertEquals("/WEB-INF/views/payment/payment-result.jsp", forwardedPath);
        assertFalse((Boolean) requestAttributes.get("success"));
        assertEquals("TXN-1", requestAttributes.get("txnRef"));
        assertEquals(new BigDecimal("100000"), requestAttributes.get("amount"));
    }

    @Test
    void unknownPathReturns404() throws Exception {
        servlet.doGet(mockRequest("GET", "/api/payments/payos/unknown"), mockResponse());
        assertEquals(404, responseStatus.get());
        servlet.doPost(mockRequest("POST", "/api/payments/payos/return"), mockResponse());
        assertEquals(404, responseStatus.get());
    }

    private HttpServletRequest mockRequest(String method, String uri) {
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "getMethod" -> method;
                    case "getRequestURI" -> uri;
                    case "getContextPath" -> "";
                    case "getParameter" -> requestParams.get((String) args[0]);
                    case "getParameterNames" -> Collections.enumeration(requestParams.keySet());
                    case "getHeader" -> requestHeaders.get((String) args[0]);
                    case "getInputStream" -> inputStream(requestBody);
                    case "getAttribute" -> requestAttributes.get((String) args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getRequestDispatcher" -> {
                        forwardedPath = (String) args[0];
                        yield (RequestDispatcher) Proxy.newProxyInstance(getClass().getClassLoader(),
                                new Class<?>[]{RequestDispatcher.class}, (p, rm, rargs) -> null);
                    }
                    default -> null;
                });
    }

    private static ServletInputStream inputStream(String body) {
        ByteArrayInputStream bytes = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
        return new ServletInputStream() {
            @Override public int read() { return bytes.read(); }
            @Override public int read(byte[] b, int off, int len) { return bytes.read(b, off, len); }
            @Override public boolean isFinished() { return bytes.available() == 0; }
            @Override public boolean isReady() { return true; }
            @Override public void setReadListener(ReadListener listener) { }
        };
    }

    private HttpServletResponse mockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, m, args) -> switch (m.getName()) {
                    case "setStatus", "sendError" -> {
                        responseStatus.set((int) args[0]);
                        yield null;
                    }
                    case "getStatus" -> responseStatus.get();
                    case "getWriter" -> new PrintWriter(responseOutput, true);
                    default -> null;
                });
    }
}
