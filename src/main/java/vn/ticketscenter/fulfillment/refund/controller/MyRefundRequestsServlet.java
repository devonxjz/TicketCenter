package vn.ticketscenter.fulfillment.refund.controller;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;

@WebServlet(
        name = "MyRefundRequestsServlet",
        urlPatterns = {"/api/me/refund-requests", "/my-refunds"})
public final class MyRefundRequestsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            var actor = RefundWeb.actor(request);
            var page =
                    RefundRuntime.resolve(getServletContext())
                            .list(
                                    actor,
                                    RefundWeb.page(request.getParameter("page"), 1),
                                    RefundWeb.page(request.getParameter("pageSize"), 20));
            if (RefundWeb.path(request).startsWith("/api/")) {
                HttpResponses.data(response, page.json());
                return;
            }
            request.setAttribute("refundPage", page);
            response.setHeader("Cache-Control", "no-store");
            request.getRequestDispatcher("/WEB-INF/views/refund/my-refunds.jsp")
                    .forward(request, response);
        } catch (RuntimeException failure) {
            RefundWeb.error(response, failure);
        }
    }
}
