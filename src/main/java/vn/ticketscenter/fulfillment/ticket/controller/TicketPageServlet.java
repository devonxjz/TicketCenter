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
import vn.ticketscenter.identity.security.LoginRedirect;
import vn.ticketscenter.identity.security.SecurityContext;
import vn.ticketscenter.identity.security.UserPrincipal;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Buyer ticket pages (LIEM-05): {@code GET /me/tickets} lists my tickets and
 * {@code GET /tickets/{ticketId}} shows one ticket with its check-in QR code.
 */
@WebServlet(urlPatterns = {"/me/tickets", "/tickets/*"})
public class TicketPageServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        UserPrincipal user = SecurityContext.getCurrentUser(request).orElse(null);
        if (user == null) {
            LoginRedirect.send(request, response);
            return;
        }

        // perform action and set URL to the matching view
        String path = ApiRequest.path(request);
        String url;
        try {
            if (path.equals("/me/tickets")) {
                url = showMyTickets(request, user);
            } else if (path.matches("^/tickets/[^/]+$")) {
                url = showTicketDetail(request, user, path);
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
        } catch (TicketException e) {
            response.sendError(e.status(), e.getMessage());
            return;
        } catch (IllegalArgumentException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid ticket ID");
            return;
        }

        // forward request and response objects to specified URL
        request.getRequestDispatcher(url).forward(request, response);
    }

    private String showMyTickets(HttpServletRequest request, UserPrincipal user) {
        List<TicketSummaryDto> tickets = service().getMyTickets(user.actorId());
        request.setAttribute("tickets", tickets);
        return "/WEB-INF/views/ticket/my-tickets.jsp";
    }

    private String showTicketDetail(HttpServletRequest request, UserPrincipal user, String path) {
        UUID ticketId = UUID.fromString(path.split("/")[2]);
        TicketDetailDto ticket = service().getTicketDetail(user.actorId(), ticketId);
        request.setAttribute("ticket", ticket);
        return "/WEB-INF/views/ticket/ticket-detail.jsp";
    }

    private TicketService service() {
        return TicketRuntime.resolve(getServletContext());
    }
}
