package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.RecentCheckInLogDto;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;

import java.io.IOException;
import java.util.stream.Collectors;

@WebServlet(name = "EventCheckInLogsServlet", urlPatterns = "/api/check-ins/recent-internal")
public class EventCheckInLogsServlet extends HttpServlet {
    public static final String EVENT_ATTRIBUTE = EventCheckInLogsServlet.class.getName() + ".event";
    private final TicketCheckInService injected;

    public EventCheckInLogsServlet() {
        this(null);
    }

    public EventCheckInLogsServlet(TicketCheckInService service) {
        injected = service;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            var actor = CheckInWeb.actor(request);
            var event = CheckInWeb.id((String) request.getAttribute(EVENT_ATTRIBUTE));
            int limit = pageSize(request.getParameter("pageSize"));
            var service =
                    injected == null
                            ? CheckInRuntime.resolveScanner(request.getServletContext())
                            : injected;
            String logs =
                    service.recent(actor, event, limit).stream()
                            .map(RecentCheckInLogDto::toJson)
                            .collect(Collectors.joining(",", "[", "]"));
            HttpResponses.data(response, logs);
        } catch (RuntimeException exception) {
            CheckInWeb.error(response, exception);
        }
    }

    private static int pageSize(String value) {
        if (value == null) return 20;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new CheckInException(400, "INVALID_PAGE_SIZE", "Số dòng nhật ký không hợp lệ.");
        }
    }
}
