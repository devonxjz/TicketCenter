package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import java.io.IOException;
import java.util.UUID;
import java.util.stream.Collectors;

@WebServlet(
        name = "CheckInEventsServlet",
        urlPatterns = {"/api/check-ins/events", "/check-ins"})
public class CheckInEventsServlet extends HttpServlet {
    private final CheckInEventService injected;

    public CheckInEventsServlet() {
        this.injected = null;
    }

    public CheckInEventsServlet(CheckInEventService service) {
        this.injected = service;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            UUID actorId = CheckInWeb.actor(request);
            String org = request.getParameter("organizationId");
            UUID organizationId = org == null ? null : CheckInWeb.id(org);
            CheckInEventService service =
                    injected == null
                            ? CheckInRuntime.resolve(request.getServletContext())
                            : injected;
            var events = service.events(actorId, organizationId);
            boolean json =
                    "/api/check-ins/events".equals(request.getServletPath())
                            || (request.getHeader("Accept") != null
                                    && request.getHeader("Accept").contains("application/json"));
            if (json) {
                HttpResponses.data(
                        response,
                        events.stream()
                                .map(e -> e.toJson())
                                .collect(Collectors.joining(",", "[", "]")));
            } else {
                response.setHeader("Cache-Control", "no-store");
                request.setAttribute("checkInEvents", events);
                request.getRequestDispatcher("/WEB-INF/views/checkin/select-event.jsp")
                        .forward(request, response);
            }
        } catch (RuntimeException exception) {
            CheckInWeb.error(response, exception);
        }
    }
}
