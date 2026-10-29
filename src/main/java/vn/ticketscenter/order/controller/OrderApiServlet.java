package vn.ticketscenter.order.controller;

import java.io.PrintWriter;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.payment.dto.PaymentInitiationResult;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.service.*;
import vn.ticketscenter.payment.service.PaymentException;
import vn.ticketscenter.payment.service.PaymentRuntime;
import vn.ticketscenter.payment.service.PaymentService;

import java.io.IOException;
import java.util.*;

@WebServlet(
        urlPatterns = {
            "/api/orders",
            "/api/orders/*",
            "/api/checkout",
            "/api/checkout/coupons",
            "/api/me/orders"
        })
public final class OrderApiServlet extends HttpServlet {
    private static final long MAX_BODY_BYTES = 16 * 1024;

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        ApiRequest.prepareJson(response);
        try {
            UserPrincipal principal =
                    SecurityContext.getCurrentUser(request)
                            .orElseThrow(
                                    () -> new OrderException(401, "UNAUTHENTICATED", "A current session is required."));
            var actor = new OrderActor(principal.actorId(), principal.authVersion());
            String path = ApiRequest.path(request);
            if (request.getMethod().equals("POST")) {
                post(request, response, actor, path);
            } else if (request.getMethod().equals("GET")) {
                get(request, response, actor, path);
            } else {
                throw missing();
            }
        } catch (OrderException | PaymentException failure) {
            HttpResponses.error(response, failure);
        } catch (OrderRequests.BodyTooLargeException failure) {
            HttpResponses.error(response, 413, "PAYLOAD_TOO_LARGE", "Order request body is too large.");
        } catch (IllegalArgumentException failure) {
            HttpResponses.error(response, 400, "INVALID_REQUEST", "Invalid Order request.");
        } catch (Exception failure) {
            HttpResponses.error(
                    response, 500, "INTERNAL_ERROR", "Không thể xử lý đơn hàng lúc này. Vui lòng thử lại.");
        }
    }

    private void post(HttpServletRequest request, HttpServletResponse response, OrderActor actor, String path)
            throws IOException {
        UUID key = mutationKey(request);
        if (path.equals("/api/orders")) {
            mutate(response, actor, new OrderCommand.Create(key, OrderRequests.create(request.getInputStream())));
        } else if (path.matches("/api/orders/[^/]+/coupon")) {
            CouponMutation coupon = OrderRequests.coupon(uuid(path.split("/")[3]), request.getInputStream());
            mutate(response, actor, new OrderCommand.ChangeCoupon(key, coupon));
        } else if (path.matches("/api/orders/[^/]+/payments")) {
            PaymentInitiationResult result = paymentService().initiate(actor.id(), uuid(path.split("/")[3]), key);
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(result.body());
        } else {
            throw missing();
        }
    }

    /** Body size, JSON media type, CSRF token and exactly one Idempotency-Key, in that order. */
    private static UUID mutationKey(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_BODY_BYTES) {
            throw new OrderException(413, "PAYLOAD_TOO_LARGE", "Order request body is too large.");
        }
        if (!ApiRequest.isJson(request)) {
            throw new OrderException(415, "CONTENT_TYPE_INVALID", "Use application/json.");
        }
        if (!SecurityContext.validateCsrfToken(request)) {
            throw new OrderException(403, "CSRF_TOKEN_INVALID", "A valid CSRF token is required.");
        }
        return uuid(ApiRequest.singleHeader(request, "Idempotency-Key").orElseThrow(OrderApiServlet::invalid));
    }

    private void mutate(HttpServletResponse response, OrderActor actor, OrderCommand command) throws IOException {
        OrderMutationReceipt receipt = service().mutate(actor, command);
        response.setStatus(receipt.httpStatus());
        response.getWriter().write(receipt.body());
    }

    private void get(HttpServletRequest request, HttpServletResponse response, OrderActor actor, String path)
            throws IOException {
        PrintWriter out = response.getWriter();
        if (path.equals("/api/checkout/coupons")) {
            out.write(service().eligibleCoupons(actor, uuid(parameter(request, "holdId"))));
        } else if (path.equals("/api/checkout")) {
            out.write(service().checkout(actor, uuid(parameter(request, "holdId"))).body());
        } else if (path.equals("/api/me/orders")) {
            int page = ApiRequest.positiveInt(request.getParameter("page"), 1);
            int size = ApiRequest.positiveInt(request.getParameter("size"), 10);
            out.write(service().orderHistory(actor, page, size, request.getParameter("status")).toJson());
        } else if (path.matches("/api/orders/[^/]+/coupon-eligibility")) {
            out.write(service().eligibility(actor, uuid(path.split("/")[3]), parameter(request, "couponCode")));
        } else if (path.matches("/api/orders/[^/]+/payment-status")) {
            out.write(paymentService().getOrderPaymentStatus(actor.id(), uuid(path.split("/")[3])).toJson());
        } else if (path.matches("/api/orders/[^/]+")) {
            out.write(service().orderDetail(actor, uuid(path.split("/")[3])).toJson());
        } else {
            throw missing();
        }
    }

    private OrderService service() {
        return OrderRuntime.resolve(getServletContext());
    }

    private PaymentService paymentService() {
        return PaymentRuntime.resolve(getServletContext());
    }

    private static String parameter(HttpServletRequest request, String name) {
        return ApiRequest.onlyParameter(request, name).orElseThrow(OrderApiServlet::invalid);
    }

    /** Canonical, non-nil UUID text. */
    private static UUID uuid(String text) {
        return ApiRequest.uuid(text).filter(id -> !id.equals(new UUID(0, 0))).orElseThrow(OrderApiServlet::invalid);
    }

    private static OrderException invalid() {
        return new OrderException(400, "INVALID_REQUEST", "Invalid Order request.");
    }

    private static OrderException missing() {
        return new OrderException(404, "NOT_FOUND", "Order endpoint was not found.");
    }

}
