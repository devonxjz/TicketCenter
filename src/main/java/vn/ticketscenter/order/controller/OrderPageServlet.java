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
 * Buyer order pages (LIEM-06 / UI-06): {@code GET /me/orders} is the order history and
 * {@code GET /orders/{orderId}} shows one order.
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
        UserPrincipal user = SecurityContext.getCurrentUser(request).orElse(null);
        if (user == null) {
            LoginRedirect.send(request, response);
            return;
        }

        // perform action and set URL to the matching view
        String path = ApiRequest.path(request);
        OrderActor actor = new OrderActor(user.actorId(), user.authVersion());
        String url;
        try {
            if (path.equals("/me/orders")) {
                url = showMyOrders(request, actor);
            } else if (path.matches("^/orders/[^/]+$")) {
                url = showOrderDetail(request, actor, path);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (OrderException e) {
            response.sendError(e.status(), e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid order request");
            return;
        }

        // forward request and response objects to specified URL
        request.getRequestDispatcher(url).forward(request, response);
    }

    private String showMyOrders(HttpServletRequest request, OrderActor actor) {
        int page = ApiRequest.positiveInt(request.getParameter("page"), 1);
        int size = ApiRequest.positiveInt(request.getParameter("size"), 10);
        String status = request.getParameter("status");

        OrderRepository.OrderHistoryPage historyPage =
                service().orderHistory(actor, page, size, status);
        request.setAttribute("historyPage", historyPage);
        request.setAttribute("currentStatus", status);
        return "/WEB-INF/views/order/my-orders.jsp";
    }

    private String showOrderDetail(HttpServletRequest request, OrderActor actor, String path) {
        UUID orderId = UUID.fromString(path.split("/")[2]);
        OrderDetailDto order = service().orderDetail(actor, orderId);
        request.setAttribute("order", order);
        return "/WEB-INF/views/order/order-detail.jsp";
    }

    private OrderService service() {
        return suppliedService != null
                ? suppliedService
                : OrderRuntime.resolve(getServletContext());
    }
}
