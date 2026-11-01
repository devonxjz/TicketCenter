package vn.ticketscenter.fulfillment.ticket.service;

import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import java.util.List;
import java.util.UUID;

/**
 * Ticket reads and signed QR codes (LIEM-05).
 */
public interface TicketService {
    /**
     * The current user's purchased tickets (owner only).
     */
    List<TicketSummaryDto> getMyTickets(UUID actorId);

    List<TicketSummaryDto> getMyTickets(UUID actorId, int authVersion);

    /**
     * One ticket in detail.
     * Access rules:
     * - only the owner (buyer) may see the ticket.
     * - an unknown ticket -> 404 NOT_FOUND.
     * - someone else's ticket -> 403 FORBIDDEN.
     */
    TicketDetailDto getTicketDetail(UUID actorId, UUID ticketId);

    TicketDetailDto getTicketDetail(UUID actorId, int authVersion, UUID ticketId);

    /**
     * The ticket's QR code as PNG bytes.
     * - only the owner may get the QR code.
     * - cached in Redis under `tc:ticket:qr:v1:<id>:<version>`.
     * - the QR holds the envelope base64url(payload).base64url(signature).
     */
    byte[] getTicketQrPng(UUID actorId, UUID ticketId, int width, int height);

    byte[] getTicketQrPng(UUID actorId, int authVersion, UUID ticketId, int width, int height);

    /**
     * Drops the cache when a ticket changes state (check-in, refund, ...).
     */
    void invalidate(UUID ticketId);
}
