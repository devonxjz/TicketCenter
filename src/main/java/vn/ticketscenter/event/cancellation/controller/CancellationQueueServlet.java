package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.ticketscenter.config.persistence.PersistenceListener;
import vn.ticketscenter.config.persistence.PersistenceRegistry;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.cancellation.service.CancellationReviewRepository;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

@WebServlet(name = "CancellationQueueServlet", urlPatterns = "/api/admin/event-cancellations")
public final class CancellationQueueServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        try {
            var actor = EventCancellationWeb.actor(request);
            Object configured = request.getServletContext().getAttribute(PersistenceListener.REGISTRY_ATTRIBUTE);
            if (!(configured instanceof PersistenceRegistry registry)) {
                throw new RefundException(503, "DATABASE_UNAVAILABLE", "Hệ thống hủy chưa sẵn sàng.");
            }
            int page = number(request, "page", 1);
            int size = number(request, "pageSize", 6);
            if (page > 50000 || size > 100) throw EventCancellationWeb.invalid();
            size = Math.min(6, size);
            HttpResponses.data(response, new CancellationReviewRepository(registry.transactionManager()).queue(actor, page, size));
        } catch (RuntimeException failure) {
            EventCancellationWeb.error(response, failure);
        }
    }

    private static int number(HttpServletRequest request, String name, int fallback) {
        String[] values = request.getParameterValues(name);
        if (values == null) return fallback;
        if (values.length != 1 || !values[0].matches("[1-9][0-9]{0,5}")) throw EventCancellationWeb.invalid();
        return Integer.parseInt(values[0]);
    }
}
