package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;

/** Forward only the refund subresource; leave every other Order route with its owner. */
@WebFilter(
        filterName = "RefundOrderRoutesFilter",
        urlPatterns = {"/api/orders/*", "/refund-request"},
        dispatcherTypes = DispatcherType.REQUEST)
public final class RefundOrderRoutesFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = RefundWeb.path(httpRequest), name;
        if (path.equals("/refund-request")) name = "RefundRequestServlet";
        else if (path.matches("/api/orders/[^/]+/refundable-tickets")) {
            name = "OrderRefundableTicketsServlet";
            request.setAttribute(
                    OrderRefundableTicketsServlet.ORDER_ID_ATTRIBUTE,
                    path.substring(12, path.length() - 19));
        } else {
            chain.doFilter(request, response);
            return;
        }
        if (!"GET".equals(httpRequest.getMethod())) {
            ((HttpServletResponse) response).setHeader("Allow", "GET");
            HttpResponses.error(
                    (HttpServletResponse) response,
                    405,
                    "METHOD_NOT_ALLOWED",
                    "Đường dẫn này chỉ hỗ trợ GET.");
            return;
        }
        var dispatcher = request.getServletContext().getNamedDispatcher(name);
        if (dispatcher == null) {
            HttpResponses.error(
                    (HttpServletResponse) response,
                    503,
                    "REFUND_UNAVAILABLE",
                    "Hệ thống hoàn vé chưa sẵn sàng.");
            return;
        }
        vn.ticketscenter.identity.security.DatabaseActorFilter.withActor(
                request, response, dispatcher::forward);
    }
}
