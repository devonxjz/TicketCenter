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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Controller phục vụ giao diện trang người dùng cho Vé (LIEM-05).
 * - GET /me/tickets: Màn hình danh sách vé của tôi.
 * - GET /tickets/{ticketId}: Màn hình chi tiết vé & mã QR check-in.
 */
@WebServlet(urlPatterns = {"/me/tickets", "/tickets/*"})
public class TicketPageServlet extends HttpServlet {
    private final TicketService suppliedService;

    public TicketPageServlet() {
        this.suppliedService = null;
    }

    public TicketPageServlet(TicketService suppliedService) {
        this.suppliedService = suppliedService;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = ApiRequest.path(request);

        var currentUserOpt = SecurityContext.getCurrentUser(request);
        if (currentUserOpt.isEmpty()) {
            String target = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank())
                target += "?" + request.getQueryString();
            response.sendRedirect(
                    request.getContextPath()
                            + "/auth/login?redirect="
                            + URLEncoder.encode(target, StandardCharsets.UTF_8));
            return;
        }

        UserPrincipal principal = currentUserOpt.get();
        UUID actorId = principal.actorId();

        try {
            if ("/me/tickets".equals(path)) {
                List<TicketSummaryDto> tickets = service().getMyTickets(actorId);
                request.setAttribute("tickets", tickets);
                request.getRequestDispatcher("/WEB-INF/views/ticket/my-tickets.jsp")
                        .forward(request, response);
            } else if (path.matches("^/tickets/[^/]+$")) {
                UUID ticketId = UUID.fromString(path.split("/")[2]);
                TicketDetailDto ticket = service().getTicketDetail(actorId, ticketId);
                request.setAttribute("ticket", ticket);
                request.getRequestDispatcher("/WEB-INF/views/ticket/ticket-detail.jsp")
                        .forward(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (TicketException e) {
            response.sendError(e.status(), e.getMessage());
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid ticket ID");
        }
    }

    private TicketService service() {
        return suppliedService != null
                ? suppliedService
                : TicketRuntime.resolve(getServletContext());
    }
}
