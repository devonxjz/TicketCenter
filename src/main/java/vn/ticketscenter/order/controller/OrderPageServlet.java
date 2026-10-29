package vn.ticketscenter.order.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.identity.security.LoginRedirect;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.order.dto.OrderActor;
import vn.ticketscenter.order.dto.OrderDetailDto;
import vn.ticketscenter.order.dao.OrderRepository;
import vn.ticketscenter.order.service.OrderException;
import vn.ticketscenter.order.service.OrderRuntime;
import vn.ticketscenter.order.service.OrderService;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Controller phục vụ giao diện trang người dùng cho Đơn hàng (LIEM-06 / UI-06).
 * - GET /me/orders: Lịch sử đơn hàng của người dùng.
 * - GET /orders/{orderId}: Chi tiết đơn hàng.
 */
@WebServlet(urlPatterns = {"/me/orders", "/orders/*"})
public class OrderPageServlet extends HttpServlet {
    private final OrderService suppliedService;

    public OrderPageServlet() {
        this.suppliedService = null;
    }

    public OrderPageServlet(OrderService suppliedService) {
        this.suppliedService = Objects.requireNonNull(suppliedService);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = ApiRequest.path(request);

        var userOpt = SecurityContext.getCurrentUser(request);
        if (userOpt.isEmpty()) {
            LoginRedirect.send(request, response);
            return;
        }

        UserPrincipal principal = userOpt.get();
        OrderActor actor = new OrderActor(principal.actorId(), principal.authVersion());

        try {
            if ("/me/orders".equals(path)) {
                int page = ApiRequest.positiveInt(request.getParameter("page"), 1);
                int size = ApiRequest.positiveInt(request.getParameter("size"), 10);
                String status = request.getParameter("status");

                OrderRepository.OrderHistoryPage historyPage =
                        service().orderHistory(actor, page, size, status);
                request.setAttribute("historyPage", historyPage);
                request.setAttribute("currentStatus", status);
                request.getRequestDispatcher("/WEB-INF/views/order/my-orders.jsp")
                        .forward(request, response);
            } else if (path.matches("^/orders/[^/]+$")) {
                UUID orderId = UUID.fromString(path.split("/")[2]);
                OrderDetailDto order = service().orderDetail(actor, orderId);
                request.setAttribute("order", order);
                request.getRequestDispatcher("/WEB-INF/views/order/order-detail.jsp")
                        .forward(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (OrderException e) {
            response.sendError(e.status(), e.getMessage());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid order request");
        }
    }

    private OrderService service() {
        return suppliedService != null
                ? suppliedService
                : OrderRuntime.resolve(getServletContext());
    }

}
