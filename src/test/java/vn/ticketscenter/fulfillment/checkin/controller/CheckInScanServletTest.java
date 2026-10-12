package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import vn.ticketscenter.config.persistence.TransactionManager;
import vn.ticketscenter.fulfillment.checkin.repository.CheckInScanRepository;
import vn.ticketscenter.fulfillment.checkin.service.CheckInScanService;
import vn.ticketscenter.fulfillment.ticket.service.SignedTicketQrSigner;
import vn.ticketscenter.identity.model.PlatformRole;
import vn.ticketscenter.identity.repository.UserRepository;
import vn.ticketscenter.identity.security.AuthSecurityListener;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.identity.service.AuthService;
import vn.ticketscenter.identity.service.PasswordHasher;

import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CheckInScanServletTest {
    private static final UUID EVENT = UUID.randomUUID(), ACTOR = UUID.randomUUID(), TICKET = UUID.randomUUID();
    private final SignedTicketQrSigner signer = new SignedTicketQrSigner("scan-http-test-key");
    private final RecordingRepository repository = new RecordingRepository();
    private final Map<String, Object> requestAttributes = new HashMap<>();
    private final Map<String, Object> sessionAttributes = new HashMap<>();
    private final Map<String, Object> contextAttributes = new HashMap<>();
    private final StringWriter output = new StringWriter();
    private int status = 200;
    private String body;
    private String csrfHeader = "test-csrf";

    CheckInScanServletTest() {
        requestAttributes.put(SecurityContext.REQUEST_USER_ATTRIBUTE, new UserPrincipal(ACTOR, PlatformRole.CUSTOMER, 1));
        sessionAttributes.put(SecurityContext.CSRF_TOKEN_ATTRIBUTE, "test-csrf");
        contextAttributes.put(AuthSecurityListener.AUTH_SERVICE_ATTRIBUTE,
                new AuthService(new TransactionManager(Map.of()), new UserRepository(), new PasswordHasher()) {
                    @Override public boolean isSessionValid(UUID actor, int version) { return actor.equals(ACTOR) && version == 1; }
                });
    }

    @Test void signedQrRequestReachesCheckInAndReturnsCanonicalResult() throws Exception {
        body = new JSONObject().put("eventId", EVENT.toString()).put("qrEnvelope", signer.buildEnvelope(TICKET, "Concert A")).toString();
        new CheckInScanServlet(service()).doPost(request(), response());
        assertEquals(200, status);
        assertEquals(TICKET, repository.ticketId);
        JSONObject data = new JSONObject(output.toString()).getJSONObject("data");
        assertEquals("SUCCESS", data.getString("result"));
        assertEquals(TICKET.toString(), data.getString("ticketId"));
    }

    @Test void invalidCsrfDoesNotCheckIn() throws Exception {
        csrfHeader = "wrong";
        body = new JSONObject().put("eventId", EVENT.toString()).put("ticketCode", "TC-123").toString();
        new CheckInScanServlet(service()).doPost(request(), response());
        assertEquals(403, status);
        assertNull(repository.ticketCode);
    }

    private CheckInScanService service() { return new CheckInScanService(repository, signer); }
    private HttpServletRequest request() {
        HttpSession session = (HttpSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpSession.class},
                (self, method, args) -> method.getName().equals("getAttribute") ? sessionAttributes.get(args[0]) : null);
        ServletContext context = (ServletContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ServletContext.class},
                (self, method, args) -> method.getName().equals("getAttribute") ? contextAttributes.get(args[0]) : null);
        return (HttpServletRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletRequest.class},
                (self, method, args) -> switch (method.getName()) {
                    case "getAttribute" -> requestAttributes.get(args[0]);
                    case "getSession" -> session;
                    case "getServletContext" -> context;
                    case "getHeader" -> SecurityContext.CSRF_HEADER_NAME.equals(args[0]) ? csrfHeader : null;
                    case "getContentType" -> "application/json";
                    case "getContentLengthLong" -> (long) body.length();
                    case "getReader" -> new java.io.BufferedReader(new StringReader(body));
                    default -> null;
                });
    }
    private HttpServletResponse response() {
        return (HttpServletResponse) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpServletResponse.class},
                (self, method, args) -> switch (method.getName()) {
                    case "setStatus" -> { status = (int) args[0]; yield null; }
                    case "getWriter" -> new PrintWriter(output);
                    default -> null;
                });
    }

    private static final class RecordingRepository extends CheckInScanRepository {
        private UUID ticketId;
        private String ticketCode;
        @Override public ScanResult scanSignedTicket(UUID eventId, UUID actorId, UUID ticketId) {
            assertEquals(EVENT, eventId); assertEquals(ACTOR, actorId);
            this.ticketId = ticketId;
            return new ScanResult("SUCCESS", Instant.EPOCH, ticketId);
        }
        @Override public ScanResult scanTicketCode(UUID eventId, UUID actorId, String ticketCode) {
            this.ticketCode = ticketCode;
            return new ScanResult("SUCCESS", Instant.EPOCH, TICKET);
        }
    }
}
