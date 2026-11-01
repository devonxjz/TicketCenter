package vn.ticketscenter.fulfillment.refund.controller;

import java.util.UUID;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.fulfillment.refund.service.RefundRuntime;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;

@WebServlet(
        name = "OrderRefundableTicketsServlet",
        urlPatterns = {})
public final class OrderRefundableTicketsServlet extends HttpServlet {
    static final String ORDER_ID_ATTRIBUTE =
            OrderRefundableTicketsServlet.class.getName() + ".orderId";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            UserPrincipal actor = RefundWeb.actor(request);
            UUID id = RefundWeb.id((String) request.getAttribute(ORDER_ID_ATTRIBUTE));
            HttpResponses.data(
                    response,
                    RefundDetailDto.ticketJson(
                            RefundRuntime.resolve(getServletContext()).tickets(actor, id)));
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }
}
