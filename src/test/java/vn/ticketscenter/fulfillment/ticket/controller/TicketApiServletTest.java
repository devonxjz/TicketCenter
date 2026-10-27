package vn.ticketscenter.fulfillment.ticket.controller;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.service.TicketException;
import vn.ticketscenter.fulfillment.ticket.service.TicketService;
import vn.ticketscenter.fulfillment.ticket.service.TicketServiceImpl;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TicketApiServletTest {
    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TICKET_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Map<String, Object> requestAttributes;
    private Map<String, String> requestParams;
    private StringWriter responseWriter;
    private ByteArrayOutputStream responseBytes;
    private AtomicInteger responseStatus;
    private String responseContentType;
    private StubTicketService service;
    private TicketApiServlet servlet;

    private static class StubTicketService extends TicketServiceImpl {
        TicketDetailDto detailToReturn;
        List<TicketSummaryDto> summariesToReturn = new ArrayList<>();
        byte[] qrBytesToReturn = new byte[]{0x10, 0x20, 0x30};

        public StubTicketService() {
            super();
        }

        @Override
        public List<TicketSummaryDto> getMyTickets(UUID actorId) {
            if (actorId == null) throw TicketException.unauthenticated();
            return summariesToReturn;
        }

        @Override
        public List<TicketSummaryDto> getMyTickets(UUID actorId, int authVersion) {
            return getMyTickets(actorId);
        }

        @Override
        public TicketDetailDto getTicketDetail(UUID actorId, UUID ticketId) {
            if (actorId == null) throw TicketException.unauthenticated();
            if (ticketId == null) throw TicketException.badRequest("ticketId is required");
            if (OTHER_ID.equals(actorId)) throw TicketException.forbidden();
            if (TICKET_ID.equals(ticketId) && detailToReturn != null) {
                return detailToReturn;
            }
            throw TicketException.notFound();
        }

        @Override
        public TicketDetailDto getTicketDetail(UUID actorId, int authVersion, UUID ticketId) {
            return getTicketDetail(actorId, ticketId);
        }

        @Override
        public byte[] getTicketQrPng(UUID actorId, UUID ticketId, int width, int height) {
            if (actorId == null) throw TicketException.unauthenticated();
            if (ticketId == null) throw TicketException.badRequest("ticketId is required");
            if (OTHER_ID.equals(actorId)) throw TicketException.forbidden();
            if (TICKET_ID.equals(ticketId)) {
                return qrBytesToReturn;
            }
            throw TicketException.notFound();
        }

        @Override
        public byte[] getTicketQrPng(UUID actorId, int authVersion, UUID ticketId, int width, int height) {
            return getTicketQrPng(actorId, ticketId, width, height);
        }
    }

    @BeforeEach
    void setUp() {
        requestAttributes = new HashMap<>();
        requestParams = new HashMap<>();
        responseWriter = new StringWriter();
        responseBytes = new ByteArrayOutputStream();
        responseStatus = new AtomicInteger(200);

        service = new StubTicketService();
        service.detailToReturn = new TicketDetailDto(
                TICKET_ID, UUID.randomUUID(), "ORD-100", "TCK-100", UUID.randomUUID(),
                "Summer Music Fest", "My Dinh Stadium", "Hanoi",
                Instant.now(), Instant.now().plusSeconds(7200), "VIP Zone", "Seat A1",
                250000L, "ACTIVE", Instant.now(), 1L
        );
        service.summariesToReturn.add(new TicketSummaryDto(
                TICKET_ID, UUID.randomUUID(), "TCK-100", "Summer Music Fest",
                "My Dinh Stadium", Instant.now(), "VIP Zone", "Seat A1",
                250000L, "ACTIVE", Instant.now(), 1L
        ));

        servlet = new TicketApiServlet(service);
    }

    @Test
    void myTicketsReturns401WhenUnauthenticated() throws Exception {
        HttpServletRequest req = mockRequest("/api/me/tickets");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(401, responseStatus.get());
        JSONObject json = new JSONObject(responseWriter.toString());
        assertEquals("UNAUTHENTICATED", json.getString("error"));
    }

    @Test
    void myTicketsReturns200WithTicketsArray() throws Exception {
        authenticate(ACTOR_ID);
        HttpServletRequest req = mockRequest("/api/me/tickets");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseWriter.toString());
        assertTrue(json.has("tickets"));
        assertEquals(1, json.getJSONArray("tickets").length());
        assertEquals("TCK-100", json.getJSONArray("tickets").getJSONObject(0).getString("ticketCode"));
    }

    @Test
    void ticketDetailReturns200ForOwner() throws Exception {
        authenticate(ACTOR_ID);
        HttpServletRequest req = mockRequest("/api/tickets/" + TICKET_ID);
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(200, responseStatus.get());
        JSONObject json = new JSONObject(responseWriter.toString());
        assertEquals(TICKET_ID.toString(), json.getString("id"));
        assertEquals("Summer Music Fest", json.getString("eventTitle"));
        assertEquals(250000L, json.getLong("paidAmount"));
    }

    @Test
    void ticketDetailReturns403ForDifferentUser() throws Exception {
        authenticate(OTHER_ID);
        HttpServletRequest req = mockRequest("/api/tickets/" + TICKET_ID);
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(403, responseStatus.get());
        JSONObject json = new JSONObject(responseWriter.toString());
        assertEquals("FORBIDDEN", json.getString("error"));
    }

    @Test
    void ticketDetailReturns400OnInvalidUuid() throws Exception {
        authenticate(ACTOR_ID);
        HttpServletRequest req = mockRequest("/api/tickets/not-a-valid-uuid");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(400, responseStatus.get());
        JSONObject json = new JSONObject(responseWriter.toString());
        assertEquals("BAD_REQUEST", json.getString("error"));
    }

    @Test
    void ticketQrReturnsPngBytes() throws Exception {
        authenticate(ACTOR_ID);
        HttpServletRequest req = mockRequest("/api/tickets/" + TICKET_ID + "/qr");
        HttpServletResponse res = mockResponse();

        servlet.doGet(req, res);

        assertEquals(200, responseStatus.get());
        assertEquals("image/png", responseContentType);
        assertArrayEquals(service.qrBytesToReturn, responseBytes.toByteArray());
    }

    private void authenticate(UUID actorId) {
        requestAttributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, new UserPrincipal(actorId, PlatformRole.CUSTOMER, 1));
    }

    private HttpServletRequest mockRequest(String path) {
        return (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getRequestURI" -> path;
                    case "getContextPath" -> "";
                    case "getAttribute" -> requestAttributes.get(args[0]);
                    case "setAttribute" -> {
                        requestAttributes.put((String) args[0], args[1]);
                        yield null;
                    }
                    case "getParameter" -> requestParams.get(args[0]);
                    case "getHeader" -> null;
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private HttpServletResponse mockResponse() {
        return (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{HttpServletResponse.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "setStatus" -> {
                        responseStatus.set((Integer) args[0]);
                        yield null;
                    }
                    case "setContentType" -> {
                        responseContentType = (String) args[0];
                        yield null;
                    }
                    case "setHeader", "setContentLength" -> null;
                    case "getWriter" -> new PrintWriter(responseWriter);
                    case "getOutputStream" -> new ServletOutputStream() {
                        @Override public boolean isReady() { return true; }
                        @Override public void setWriteListener(WriteListener writeListener) { }
                        @Override public void write(int b) { responseBytes.write(b); }
                    };
                    default -> defaultValue(method.getReturnType());
                }
        );
    }

    private static Object defaultValue(Class<?> returnType) {
        if (returnType == void.class) return null;
        if (returnType == boolean.class) return false;
        if (returnType == int.class) return 0;
        if (returnType == long.class) return 0L;
        return null;
    }
}
