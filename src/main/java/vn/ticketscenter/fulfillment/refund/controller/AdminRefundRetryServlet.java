package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.refund.dto.AdminRefundRetryRequestDto;

@WebServlet(name = "AdminRefundRetryServlet", urlPatterns = "/api/admin/refund-retry-internal")
public final class AdminRefundRetryServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            var id = AdminRefundWeb.target(request, "retry");
            var actor = AdminRefundWeb.actor(request);
            var key = RefundWeb.mutation(request);
            String body = AdminRefundWeb.body(request);
            AdminRefundJson.retry(body.isBlank() ? "{}" : body);
            var result =
                    AdminRefundRuntime.resolve(request.getServletContext())
                            .retry(actor, id, new AdminRefundRetryRequestDto(key));
            HttpResponses.data(response, result.json());
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }
}
