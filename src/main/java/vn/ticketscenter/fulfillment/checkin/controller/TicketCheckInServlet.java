package vn.ticketscenter.fulfillment.checkin.controller;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.fulfillment.checkin.CheckInException;
import vn.ticketscenter.fulfillment.checkin.dto.CheckInScanRequestDto;
import vn.ticketscenter.fulfillment.checkin.service.TicketCheckInService;
import vn.ticketscenter.identity.security.SecurityContext;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@WebServlet(name = "TicketCheckInServlet", urlPatterns = "/api/check-ins")
public class TicketCheckInServlet extends HttpServlet {
    private static final int MAX_BODY_LENGTH = 8192;
    private final TicketCheckInService injected;

    public TicketCheckInServlet() { this(null); }
    public TicketCheckInServlet(TicketCheckInService service) { injected = service; }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            UUID actor = CheckInWeb.actor(request);
            if (!SecurityContext.validateCsrfToken(request)) {
                throw new CheckInException(403, "CSRF_TOKEN_INVALID", "Mã CSRF không hợp lệ.");
            }
            String contentType = request.getContentType();
            if (contentType == null || !contentType.split(";")[0].trim().equalsIgnoreCase("application/json")) {
                throw new CheckInException(415, "JSON_REQUIRED", "Yêu cầu phải dùng application/json.");
            }

            UUID key = CheckInWeb.id(request.getHeader("Idempotency-Key"));
            var dto = readRequest(request);
            var service = injected == null ? CheckInRuntime.resolveScanner(request.getServletContext()) : injected;
            var result = service.scan(actor, key, dto);
            response.setStatus(result.httpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"data\":" + result.toJson() + "}");
        } catch (RuntimeException exception) {
            CheckInWeb.error(response, exception);
        }
    }

    private static CheckInScanRequestDto readRequest(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > MAX_BODY_LENGTH) throw oversized();

        char[] chars = new char[MAX_BODY_LENGTH + 1];
        Reader reader = request.getReader();
        int count = 0, read;
        while (count < chars.length && (read = reader.read(chars, count, chars.length - count)) != -1) {
            count += read;
        }
        if (count > MAX_BODY_LENGTH) throw oversized();

        Map<String, String> body;
        try {
            body = JsonObjectParser.parse(new StringReader(new String(chars, 0, count)));
        } catch (IllegalArgumentException exception) {
            throw new CheckInException(400, "INVALID_JSON", "Nội dung JSON không hợp lệ.");
        }
        if (!body.keySet().equals(Set.of("eventId", "qrContent"))
                && !body.keySet().equals(Set.of("eventId", "ticketCode"))) {
            throw new CheckInException(400, "INVALID_SCAN_REQUEST", "Nội dung soát vé không đúng định dạng.");
        }
        return new CheckInScanRequestDto(CheckInWeb.id(body.get("eventId")), body.get("qrContent"), body.get("ticketCode"));
    }

    private static CheckInException oversized() {
        return new CheckInException(413, "REQUEST_TOO_LARGE", "Nội dung soát vé quá dài.");
    }
}
