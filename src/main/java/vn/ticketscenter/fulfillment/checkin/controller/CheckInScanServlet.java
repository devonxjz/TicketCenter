package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;
import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.service.CheckInScanService;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

@WebServlet(name = "CheckInScanServlet", urlPatterns = "/api/check-ins")
public final class CheckInScanServlet extends HttpServlet {
    private final CheckInScanService injected;

    public CheckInScanServlet() { this.injected = null; }
    public CheckInScanServlet(CheckInScanService service) { this.injected = service; }

    @Override protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UUID actorId = CheckInWeb.actor(request);
            if (!SecurityContext.validateCsrfToken(request))
                throw new CheckInException(403, "CSRF_TOKEN_INVALID", "Phiên bảo mật không hợp lệ.");
            if (request.getContentType() == null || !request.getContentType().split(";", 2)[0].trim().equalsIgnoreCase("application/json"))
                throw new CheckInException(415, "CONTENT_TYPE_INVALID", "Yêu cầu JSON.");
            String body = readBody(request);
            JSONObject input;
            try { input = new JSONObject(body); }
            catch (RuntimeException invalid) { throw new CheckInException(400, "INVALID_REQUEST", "Dữ liệu quét không hợp lệ."); }
            boolean qr = input.keySet().equals(Set.of("eventId", "qrEnvelope"));
            if (!qr && !input.keySet().equals(Set.of("eventId", "ticketCode")))
                throw new CheckInException(400, "INVALID_REQUEST", "Cần sự kiện và đúng một mã QR hoặc mã vé.");
            UUID eventId;
            String value;
            try {
                eventId = CheckInWeb.id(input.getString("eventId"));
                value = input.getString(qr ? "qrEnvelope" : "ticketCode");
            }
            catch (RuntimeException invalid) { throw new CheckInException(400, "INVALID_REQUEST", "Dữ liệu quét không hợp lệ."); }
            CheckInScanService service = injected == null ? CheckInScanRuntime.resolve(request.getServletContext()) : injected;
            var result = qr ? service.scanQr(eventId, actorId, value) : service.scanCode(eventId, actorId, value);
            HttpResponses.data(response, new JSONObject()
                    .put("result", result.result())
                    .put("scannedAt", result.scannedAt().toString())
                    .put("ticketId", result.ticketId() == null ? JSONObject.NULL : result.ticketId().toString())
                    .toString());
        } catch (RuntimeException failure) { CheckInWeb.error(response, failure); }
    }

    private static String readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > 4096)
            throw new CheckInException(413, "PAYLOAD_TOO_LARGE", "Dữ liệu quét quá dài.");
        StringBuilder body = new StringBuilder();
        try (var reader = request.getReader()) {
            int ch;
            while ((ch = reader.read()) != -1) {
                if (body.length() == 4096) throw new CheckInException(413, "PAYLOAD_TOO_LARGE", "Dữ liệu quét quá dài.");
                body.append((char) ch);
            }
        }
        return body.toString();
    }
}
