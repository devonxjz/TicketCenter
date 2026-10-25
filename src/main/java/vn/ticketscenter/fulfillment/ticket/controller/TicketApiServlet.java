package vn.ticketscenter.fulfillment.ticket.controller;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.ticketscenter.config.web.ApiRequest;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.service.TicketException;
import vn.ticketscenter.fulfillment.ticket.service.TicketRuntime;
import vn.ticketscenter.fulfillment.ticket.service.TicketService;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Controller API quản lý vé và mã QR (LIEM-05).
 * - GET /api/me/tickets: Danh sách vé của user hiện tại.
 * - GET /api/tickets/{ticketId}: Chi tiết một vé.
 * - GET /api/tickets/{ticketId}/qr: Trả về ảnh PNG mã QR ký số.
 */
@WebServlet(urlPatterns = {"/api/me/tickets", "/api/tickets/*"})
public class TicketApiServlet extends HttpServlet {
    private final TicketService suppliedService;

    public TicketApiServlet() {
        this.suppliedService = null;
    }

    public TicketApiServlet(TicketService suppliedService) {
        this.suppliedService = suppliedService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = ApiRequest.path(request);

        try {
            UserPrincipal principal =
                    SecurityContext.getCurrentUser(request)
                            .orElseThrow(TicketException::unauthenticated);
            UUID actorId = principal.actorId();
            int authVersion = principal.authVersion();

            if ("/api/me/tickets".equals(path)) {
                handleMyTickets(actorId, authVersion, response);
            } else if (path.matches("^/api/tickets/[^/]+/qr$")) {
                UUID ticketId = parseTicketId(path.split("/")[3]);
                int size = parseSize(request.getParameter("size"));
                handleTicketQr(actorId, authVersion, ticketId, size, response);
            } else if (path.matches("^/api/tickets/[^/]+$")) {
                UUID ticketId = parseTicketId(path.split("/")[3]);
                handleTicketDetail(actorId, authVersion, ticketId, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (TicketException e) {
            response.setStatus(e.httpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(e.toJson());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            java.util.logging.Logger.getLogger(TicketApiServlet.class.getName())
                    .log(java.util.logging.Level.SEVERE, "Ticket request failed", e);
            response.getWriter()
                    .write(
                            "{\"error\":\"INTERNAL_ERROR\",\"message\":\"Đã xảy ra lỗi hệ thống.\"}");
        }
    }

    private void handleMyTickets(UUID actorId, int authVersion, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        List<TicketSummaryDto> tickets = service().getMyTickets(actorId, authVersion);
        String jsonArray =
                tickets.stream().map(TicketSummaryDto::toJson).collect(Collectors.joining(","));

        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"tickets\":[" + jsonArray + "]}");
    }

    private void handleTicketDetail(
            UUID actorId, int authVersion, UUID ticketId, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("Cache-Control", "no-store");

        TicketDetailDto detail = service().getTicketDetail(actorId, authVersion, ticketId);
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(detail.toJson());
    }

    private void handleTicketQr(
            UUID actorId, int authVersion, UUID ticketId, int size, HttpServletResponse response)
            throws IOException {
        byte[] pngBytes = service().getTicketQrPng(actorId, authVersion, ticketId, size, size);

        response.setContentType("image/png");
        response.setHeader("Cache-Control", "private, max-age=300");
        response.setContentLength(pngBytes.length);
        response.setStatus(HttpServletResponse.SC_OK);
        response.getOutputStream().write(pngBytes);
        response.getOutputStream().flush();
    }

    private TicketService service() {
        return suppliedService != null
                ? suppliedService
                : TicketRuntime.resolve(getServletContext());
    }

    private static UUID parseTicketId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw TicketException.badRequest("Invalid ticket UUID format");
        }
    }

    private static int parseSize(String raw) {
        if (raw == null || raw.isBlank()) return 300;
        try {
            int val = Integer.parseInt(raw);
            return (val >= 100 && val <= 1000) ? val : 300;
        } catch (NumberFormatException e) {
            return 300;
        }
    }
}
