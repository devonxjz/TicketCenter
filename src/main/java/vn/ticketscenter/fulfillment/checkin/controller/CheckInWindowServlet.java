package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.service.CheckInEventService;
import java.io.IOException;
import java.util.UUID;

/** Invoked by a named dispatcher for the exact nested route; owns no Event wildcard. */
@WebServlet(
        name = "CheckInWindowServlet",
        urlPatterns = {})
public class CheckInWindowServlet extends HttpServlet {
    public static final String EVENT_ID_ATTRIBUTE =
            CheckInWindowServlet.class.getName() + ".eventId";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            UUID actorId = CheckInWeb.actor(request);
            UUID eventId = CheckInWeb.id((String) request.getAttribute(EVENT_ID_ATTRIBUTE));
            CheckInEventService service =
                    CheckInRuntime.resolve(getServletContext());
            HttpResponses.data(response, service.window(actorId, eventId).toJson());
        } catch (RuntimeException exception) {
            CheckInWeb.error(response, exception);
        }
    }
}
