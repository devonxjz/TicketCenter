package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;

@WebServlet(
        name = "AdminRefundDecisionServlet",
        urlPatterns = "/api/admin/refund-decision-internal")
public final class AdminRefundDecisionServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            var id = AdminRefundWeb.target(request, "decision");
            var actor = AdminRefundWeb.actor(request);
            var key = RefundWeb.mutation(request);
            var decision = AdminRefundJson.decision(AdminRefundWeb.body(request));
            var result =
                    AdminRefundRuntime.resolve(request.getServletContext())
                            .decide(actor, id, decision, key);
            HttpResponses.data(response, result.json());
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }
}
