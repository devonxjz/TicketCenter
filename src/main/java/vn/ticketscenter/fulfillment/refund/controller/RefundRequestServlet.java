package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.service.RefundException;
import vn.ticketscenter.identity.security.SecurityContext;

@WebServlet(
        name = "RefundRequestServlet",
        urlPatterns = {
            "/api/refund-requests",
            "/api/refund-requests/*",
            "/refund-requests",
            "/refund-requests/*"
        })
public final class RefundRequestServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            var actor = RefundWeb.actor(request);
            var service = RefundRuntime.resolve(getServletContext());
            String path = RefundWeb.path(request);
            String prefix = path.startsWith("/api/") ? "/api/refund-requests" : "/refund-requests";
            if (!path.equals("/refund-request") && !path.equals(prefix)) {
                String tail = path.substring(prefix.length() + 1);
                boolean attempts = tail.endsWith("/attempts");
                var detail =
                        service.detail(
                                actor,
                                RefundWeb.id(
                                        attempts ? tail.substring(0, tail.length() - 9) : tail));
                HttpResponses.data(
                        response,
                        attempts ? RefundDetailDto.attemptJson(detail.attempts()) : detail.json());
                return;
            }
            if (path.startsWith("/api/")) {
                HttpResponses.data(
                        response,
                        service.list(
                                        actor,
                                        RefundWeb.page(request.getParameter("page"), 1),
                                        RefundWeb.page(request.getParameter("pageSize"), 20))
                                .json());
                return;
            }
            request.setAttribute("refundOrders", service.orders(actor));
            request.setAttribute("csrf", SecurityContext.getOrCreateCsrfToken(request));
            request.setAttribute("selectedOrder", request.getParameter("orderId"));
            response.setHeader("Cache-Control", "no-store");
            request.getRequestDispatcher("/WEB-INF/views/refund/request-form.jsp")
                    .forward(request, response);
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            if (!RefundWeb.path(request).equals("/api/refund-requests"))
                throw new RefundException(
                        405, "METHOD_NOT_ALLOWED", "Đường dẫn này chỉ cho phép xem yêu cầu.");
            var actor = RefundWeb.actor(request);
            var key = RefundWeb.mutation(request);
            var result =
                    RefundRuntime.resolve(getServletContext())
                            .create(actor, RefundWeb.create(request), key);
            response.setStatus(201);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter()
                    .write(
                            "{\"data\":"
                                    + result.json().substring(0, result.json().length() - 1)
                                    + ",\"message\":\"Yêu cầu hoàn tiền đã được tiếp nhận và chuyển"
                                    + " đến Ban quản trị xem xét.\"}}");
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }
}
