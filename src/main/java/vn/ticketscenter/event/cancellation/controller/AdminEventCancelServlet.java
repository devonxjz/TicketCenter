package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;

@WebServlet(name = "AdminEventCancelServlet", urlPatterns = "/api/admin/event-cancel-internal")
public final class AdminEventCancelServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var id = EventCancellationWeb.target(request, "cancel");
            var actor = EventCancellationWeb.actor(request);
            var key = EventCancellationWeb.mutation(request);
            var body = EventCancellationWeb.body(request);
            HttpResponses.data(response, EventCancellationWeb.service(request).cancel(actor, id, body, key).json());
        } catch (RuntimeException failure) {
            EventCancellationWeb.error(response, failure);
        }
    }
}
