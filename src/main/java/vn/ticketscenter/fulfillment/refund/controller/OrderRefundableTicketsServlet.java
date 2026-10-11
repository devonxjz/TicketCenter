package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;

@WebServlet(name="OrderRefundableTicketsServlet",urlPatterns={})
public final class OrderRefundableTicketsServlet extends HttpServlet {
    static final String ORDER_ID_ATTRIBUTE=OrderRefundableTicketsServlet.class.getName()+".orderId";
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException {
        try{var actor=RefundWeb.actor(request);var id=RefundWeb.id((String)request.getAttribute(ORDER_ID_ATTRIBUTE));HttpResponses.data(response,RefundDetailDto.ticketJson(RefundRuntime.resolve(getServletContext()).tickets(actor,id)));}
        catch(RuntimeException failure){RefundWeb.error(response,failure);}
    }
}
