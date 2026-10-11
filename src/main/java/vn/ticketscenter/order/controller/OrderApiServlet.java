package vn.ticketscenter.order.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.service.*;

import java.io.IOException;
import java.util.*;

@WebServlet(urlPatterns = {"/api/orders", "/api/orders/*", "/api/checkout", "/api/me/orders"})
public final class OrderApiServlet extends HttpServlet {
    private final OrderService supplied;
    private final vn.ticketscenter.payment.service.PaymentService suppliedPayment;
    public OrderApiServlet() { this.supplied = null; this.suppliedPayment = null; }
    public OrderApiServlet(OrderService supplied) { this.supplied = Objects.requireNonNull(supplied); this.suppliedPayment = null; }
    public OrderApiServlet(OrderService supplied, vn.ticketscenter.payment.service.PaymentService suppliedPayment) {
        this.supplied = supplied;
        this.suppliedPayment = suppliedPayment;
    }
    @Override protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setCharacterEncoding("UTF-8"); response.setContentType("application/json;charset=UTF-8"); response.setHeader("Cache-Control", "no-store");
        try {
            var principal = SecurityContext.getCurrentUser(request).orElseThrow(() -> new OrderException(401, "UNAUTHENTICATED", "A current session is required."));
            OrderActor actor = new OrderActor(principal.actorId(), principal.authVersion());
            String path = request.getRequestURI().substring(request.getContextPath().length());
            if (request.getMethod().equals("POST")) {
                if (request.getContentLengthLong() > 16 * 1024) throw new OrderException(413, "PAYLOAD_TOO_LARGE", "Order request body is too large.");
                String contentType = request.getContentType();
                if (contentType == null || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                    throw new OrderException(415, "CONTENT_TYPE_INVALID", "Use application/json.");
                if (!SecurityContext.validateCsrfToken(request)) throw new OrderException(403, "CSRF_TOKEN_INVALID", "A valid CSRF token is required.");
                Enumeration<String> keys = request.getHeaders("Idempotency-Key");
                if (keys == null || !keys.hasMoreElements()) throw invalid();
                UUID key = uuid(keys.nextElement());
                if (keys.hasMoreElements()) throw invalid();
                OrderCommand command;
                if (path.equals("/api/orders")) command = new OrderCommand.Create(key, OrderRequests.create(request.getInputStream()));
                else if (path.matches("/api/orders/[^/]+/coupon"))
                    command = new OrderCommand.ChangeCoupon(key, OrderRequests.coupon(uuid(path.split("/")[3]), request.getInputStream()));
                else if (path.matches("/api/orders/[^/]+/payments")) {
                    UUID orderId = uuid(path.split("/")[3]);
                    var paymentReq = vn.ticketscenter.payment.dto.PaymentRequests.parse(request.getInputStream());
                    String clientIp = clientIp(request);
                    var paymentActor = new vn.ticketscenter.payment.dto.PaymentActor(principal.actorId(), principal.authVersion());
                    var result = paymentService().initiate(paymentActor, orderId, key, paymentReq.bankCode(), clientIp);
                    response.setStatus(result.httpStatus());
                    response.getWriter().write(result.body());
                    return;
                }
                else throw missing();
                OrderMutationReceipt receipt = service().mutate(actor, command);
                response.setStatus(receipt.httpStatus()); response.getWriter().write(receipt.body());
            } else if (request.getMethod().equals("GET") && path.equals("/api/checkout")) {
                response.getWriter().write(service().checkout(actor, uuid(parameter(request, "holdId"))).body());
            } else if (request.getMethod().equals("GET") && path.equals("/api/me/orders")) {
                int page = parsePositiveInt(request.getParameter("page"), 1);
                int size = parsePositiveInt(request.getParameter("size"), 10);
                String status = request.getParameter("status");
                response.getWriter().write(service().orderHistory(actor, page, size, status).toJson());
            } else if (request.getMethod().equals("GET") && path.matches("/api/orders/[^/]+/coupon-eligibility")) {
                response.getWriter().write(service().eligibility(actor, uuid(path.split("/")[3]), parameter(request, "couponCode")));
            } else if (request.getMethod().equals("GET") && path.matches("/api/orders/[^/]+/payment-status")) {
                var paymentStatus = paymentService().getOrderPaymentStatus(actor.id(), uuid(path.split("/")[3]));
                response.getWriter().write(paymentStatus.toJson());
            } else if (request.getMethod().equals("GET") && path.matches("/api/orders/[^/]+")) {
                UUID orderId = uuid(path.split("/")[3]);
                response.getWriter().write(service().orderDetail(actor, orderId).toJson());
            } else throw missing();
        } catch (OrderException failure) { HttpResponses.error(response, failure.status(), failure.code(), failure.getMessage()); }
        catch (vn.ticketscenter.payment.service.PaymentException failure) { HttpResponses.error(response, failure.status(), failure.code(), failure.getMessage()); }
        catch (OrderRequests.BodyTooLargeException failure) { HttpResponses.error(response, 413, "PAYLOAD_TOO_LARGE", "Order request body is too large."); }
        catch (IllegalArgumentException failure) { HttpResponses.error(response, 400, "INVALID_REQUEST", "Invalid Order request."); }
    }
    private OrderService service() { return supplied == null ? OrderRuntime.resolve(getServletContext()) : supplied; }
    private vn.ticketscenter.payment.service.PaymentService paymentService() {
        return suppliedPayment == null ? vn.ticketscenter.payment.service.PaymentRuntime.resolve(getServletContext()) : suppliedPayment;
    }
    private static String clientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",", 2)[0].trim();
        }
        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }
    private static String parameter(HttpServletRequest request, String name) {
        if (!request.getParameterMap().keySet().equals(Set.of(name)) || request.getParameterValues(name).length != 1) throw invalid();
        return request.getParameter(name);
    }
    private static UUID uuid(String text) {
        if (text == null || !text.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw invalid();
        UUID id = UUID.fromString(text); if (id.equals(new UUID(0, 0))) throw invalid(); return id;
    }
    private static OrderException invalid() { return new OrderException(400, "INVALID_REQUEST", "Invalid Order request."); }
    private static OrderException missing() { return new OrderException(404, "NOT_FOUND", "Order endpoint was not found."); }
    private static int parsePositiveInt(String val, int def) {
        if (val == null || val.isBlank()) return def;
        try {
            int parsed = Integer.parseInt(val);
            return parsed > 0 ? parsed : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
