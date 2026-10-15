package vn.ticketscenter.payment.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.PayosConfig;
import vn.ticketscenter.payment.dto.PaymentReturnResult;
import vn.ticketscenter.payment.dto.PayosWebhookResult;
import vn.ticketscenter.payment.service.PaymentRuntime;
import vn.ticketscenter.payment.service.PaymentService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;


@WebServlet(urlPatterns = {PayosConfig.WEBHOOK_PATH, PayosConfig.RETURN_PATH, PayosConfig.CANCEL_PATH})
public class PayosCallbackServlet extends HttpServlet {
    private static final int MAX_WEBHOOK_BYTES = 64 * 1024;
    private final PaymentService suppliedService;

    public PayosCallbackServlet() {
        this.suppliedService = null;
    }

    public PayosCallbackServlet(PaymentService suppliedService) {
        this.suppliedService = suppliedService;
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!PayosConfig.WEBHOOK_PATH.equals(path(request))) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String body = readBody(request.getInputStream());
        PayosWebhookResult result = body == null ? PayosWebhookResult.invalidSignature() : service().processWebhook(body);
        response.setStatus(result.httpStatus());
        writeJson(response, result.toJson());
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = path(request);
        if (!PayosConfig.RETURN_PATH.equals(path) && !PayosConfig.CANCEL_PATH.equals(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        PaymentReturnResult result = service().processReturn(queryParams(request));

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setStatus(HttpServletResponse.SC_OK);
            writeJson(response, result.toJson());
            return;
        }
        request.setAttribute("paymentResult", result);
        request.setAttribute("orderId", result.orderId().map(java.util.UUID::toString).orElse(""));
        request.setAttribute("txnRef", result.txnRef());
        request.setAttribute("amount", result.amount());
        request.setAttribute("success", result.success());
        request.setAttribute("message", result.message());
        request.getRequestDispatcher("/WEB-INF/views/payment/payment-result.jsp").forward(request, response);
    }

    private PaymentService service() {
        return suppliedService != null ? suppliedService : PaymentRuntime.resolve(getServletContext());
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** @return the UTF-8 body, or null when it exceeds {@link #MAX_WEBHOOK_BYTES} */
    private static String readBody(InputStream input) throws IOException {
        byte[] body = input.readNBytes(MAX_WEBHOOK_BYTES + 1);
        return body.length > MAX_WEBHOOK_BYTES ? null : new String(body, StandardCharsets.UTF_8);
    }

    private static Map<String, String> queryParams(HttpServletRequest request) {
        Map<String, String> params = new HashMap<>();
        var names = request.getParameterNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) params.put(name, value);
        }
        return params;
    }

    private static void writeJson(HttpServletResponse response, String json) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(json);
    }
}
