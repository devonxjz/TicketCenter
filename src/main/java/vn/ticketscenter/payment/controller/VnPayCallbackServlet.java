package vn.ticketscenter.payment.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.payment.dto.VnpayIpnResult;
import vn.ticketscenter.payment.dto.VnpayReturnResult;
import vn.ticketscenter.payment.service.PaymentRuntime;
import vn.ticketscenter.payment.service.PaymentService;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/payments/vnpay/ipn", "/api/payments/vnpay/return"})
public class VnPayCallbackServlet extends HttpServlet {
    private final PaymentService suppliedService;

    public VnPayCallbackServlet() {
        this.suppliedService = null;
    }

    public VnPayCallbackServlet(PaymentService suppliedService) {
        this.suppliedService = suppliedService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Map<String, String> params = extractParams(request);

        if ("/api/payments/vnpay/ipn".equals(path)) {
            handleIpn(params, response);
        } else if ("/api/payments/vnpay/return".equals(path)) {
            handleReturn(request, response, params);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private void handleIpn(Map<String, String> params, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");
        VnpayIpnResult result = service().processIpn(params);
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(result.toJson());
    }

    private void handleReturn(HttpServletRequest request, HttpServletResponse response, Map<String, String> params)
            throws ServletException, IOException {
        VnpayReturnResult result = service().processReturn(params);

        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.toJson());
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

    private static Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> map = new HashMap<>();
        var parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String name = parameterNames.nextElement();
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                map.put(name, value);
            }
        }
        return map;
    }
}
