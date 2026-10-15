package vn.ticketscenter.event.cancellation.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import vn.ticketscenter.config.web.HttpResponses;

@WebServlet(name = "AdminCancellationProgressServlet", urlPatterns = "/api/admin/cancellation-progress-internal")
public final class AdminCancellationProgressServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            var id = EventCancellationWeb.target(request, "cancellation-progress");
            var actor = EventCancellationWeb.actor(request);
            HttpResponses.data(response, EventCancellationWeb.service(request).progress(actor, id));
        } catch (RuntimeException failure) {
            EventCancellationWeb.error(response, failure);
        }
    }
}
