package vn.ticketscenter.event.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.*;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.event.dto.EventFilter;
import vn.ticketscenter.event.service.EventCatalogService;
import java.io.IOException;
import java.util.HashMap;

@WebServlet(
        name = "EventCatalogServlet",
        urlPatterns = {"/api/events", "/api/events/*", "/api/zones/*", "/api/event-categories"},
        loadOnStartup = 1)
@MultipartConfig(maxFileSize = 5242880, maxRequestSize = 6291456, fileSizeThreshold = 0)
public class EventCatalogServlet extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        boolean validated = false;
        try {
            String route = ApiRequest.path(request);
            // Validate path/filter before resolving dependencies, so malformed requests remain
            // 400/404 during outages.
            java.util.function.Function<EventCatalogService, String> action;
            if (route.equals("/api/events")) {
                var parameters = new HashMap<String, String>();
                for (String name :
                        new String[] {
                            "keyword", "categoryId", "from", "to", "sort", "page", "pageSize"
                        }) {
                    String[] values = request.getParameterValues(name);
                    if (values != null) {
                        if (values.length != 1)
                            throw new IllegalArgumentException("Repeated filter");
                        parameters.put(name, values[0]);
                    }
                }
                var filter = EventFilter.parse(parameters);
                action = service -> service.events(filter);
            } else if (route.equals("/api/event-categories")) {
                action = EventCatalogService::categories;
            } else {
                String[] parts = route.split("/", -1);
                if (parts.length == 4 && parts[2].equals("events")) {
                    var id = EventFilter.id(parts[3]);
                    action = service -> service.event(id);
                } else if (parts.length == 5
                        && parts[2].equals("events")
                        && parts[4].equals("zones")) {
                    var id = EventFilter.id(parts[3]);
                    action = service -> service.zones(id);
                } else if (parts.length == 5
                        && parts[2].equals("zones")
                        && parts[4].equals("seats")) {
                    var id = EventFilter.id(parts[3]);
                    action = service -> service.seats(id);
                } else {
                    throw new EventCatalogService.NotFound();
                }
            }
            validated = true;
            String json =
                    action.apply(
                            EventCatalogRuntime.resolve(getServletContext()));
            response.setStatus(200);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(json);
        } catch (IllegalArgumentException exception) {
            if (validated) unavailable(response);
            else
                HttpResponses.error(
                        response,
                        400,
                        "INVALID_EVENT_QUERY",
                        "Tham số tìm kiếm hoặc ID không hợp lệ.");
        } catch (EventCatalogService.NotFound exception) {
            HttpResponses.error(
                    response,
                    404,
                    "EVENT_NOT_FOUND",
                    "Không tìm thấy sự kiện hoặc khu vực công khai.");
        } catch (RuntimeException exception) {
            unavailable(response);
        }
    }

    private static void unavailable(HttpServletResponse response) throws IOException {
        HttpResponses.error(
                response,
                503,
                "EVENT_CATALOG_UNAVAILABLE",
                "Chưa thể tải dữ liệu sự kiện. Vui lòng thử lại.");
    }
}
