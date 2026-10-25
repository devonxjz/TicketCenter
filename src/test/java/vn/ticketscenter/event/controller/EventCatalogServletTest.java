package vn.ticketscenter.event.controller;

import jakarta.servlet.http.*;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.event.cache.EventCatalogCache;
import vn.ticketscenter.event.dto.*;
import vn.ticketscenter.event.dao.EventCatalogRepository;
import vn.ticketscenter.event.service.EventCatalogService;
import vn.ticketscenter.event.service.EventCatalogServiceImpl;
import java.io.*;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EventCatalogServletTest {
    private final UUID id = UUID.randomUUID();
    private int status;
    private final StringWriter body = new StringWriter();
    private final Map<String, String> headers = new HashMap<>();
    private boolean invalidProjection;
    private final EventCatalogCache noCache = new EventCatalogCache() {
        public Optional<String> get(String key) { return Optional.empty(); }
        public void put(String key, String value, int ttl) { }
    };
    private EventCatalogRepository repository(boolean broken) {
        return new EventCatalogRepository(null) {
            @Override public EventDtos.Page events(EventFilter filter) {
                if (invalidProjection) throw new IllegalArgumentException("DB mapping detail");
                if (broken) throw new IllegalStateException("SQL secret");
                return new EventDtos.Page(List.of(), 0);
            }
            @Override public Optional<EventDtos.Event> event(UUID value) { return Optional.empty(); }
            @Override public Optional<List<EventDtos.Zone>> zones(UUID value) { return Optional.of(List.of()); }
            @Override public Optional<List<EventDtos.Seat>> seats(UUID value) { return Optional.of(List.of()); }
            @Override public List<EventDtos.Category> categories() { return List.of(); }
        };
    }
    private void request(String path, Map<String, String[]> parameters, boolean broken) throws IOException {
        var req = (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> "/tickets" + path;
                    case "getContextPath" -> "/tickets";
                    case "getParameterValues" -> parameters.get(args[0]);
                    default -> null;
                });
        var resp = (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status = (int) args[0]; yield null; }
                    case "setHeader" -> { headers.put((String) args[0], (String) args[1]); yield null; }
                    case "getWriter" -> new PrintWriter(body);
                    default -> null;
                });
        new EventCatalogServlet(new EventCatalogServiceImpl(repository(broken), noCache)).doGet(req, resp);
    }
    @Test void anonymousCatalogSupportsContextPathAndPaginationEnvelope() throws Exception {
        request("/api/events", Map.of(), false); assertEquals(200, status);
        assertEquals("{\"data\":[],\"page\":1,\"pageSize\":6,\"total\":0,\"totalPages\":0}", body.toString());
        assertEquals("no-store", headers.get("Cache-Control"));
    }
    @Test void invalidIdIs400() throws Exception { request("/api/events/not-an-id", Map.of(), false); assertEquals(400, status); }
    @Test void repeatedParametersAre400() throws Exception { request("/api/events", Map.of("page", new String[]{"1", "2"}), false); assertEquals(400, status); }
    @Test void hiddenEventIs404() throws Exception { request("/api/events/" + id, Map.of(), false); assertEquals(404, status); }
    @Test void unknownRouteIs404() throws Exception { request("/api/events/" + id + "/private", Map.of(), false); assertEquals(404, status); }
    @Test void unavailableDatabaseIs503WithoutInternalDetails() throws Exception {
        request("/api/events", Map.of(), true); assertEquals(503, status);
        assertTrue(body.toString().contains("EVENT_CATALOG_UNAVAILABLE")); assertFalse(body.toString().contains("SQL secret"));
    }
    @Test void zonesRouteIsPublic() throws Exception { request("/api/events/" + id + "/zones", Map.of(), false); assertEquals(200, status); assertEquals("{\"data\":[]}", body.toString()); }
    @Test void seatsRouteIsPublic() throws Exception { request("/api/zones/" + id + "/seats", Map.of(), false); assertEquals(200, status); }
    @Test void categoriesRouteIsPublic() throws Exception { request("/api/event-categories", Map.of(), false); assertEquals(200, status); }
    @Test void invalidProjectionIs503RatherThanClientValidation400() throws Exception {
        invalidProjection = true; request("/api/events", Map.of(), false); assertEquals(503, status);
        assertFalse(body.toString().contains("DB mapping detail"));
    }
    @Test void paginationAndSortOutsideIssueContractAre400() throws Exception {
        request("/api/events", Map.of("pageSize", new String[]{"51"}), false); assertEquals(400, status);
    }
}
