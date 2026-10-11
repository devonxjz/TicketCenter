package vn.ticketscenter.fulfillment.ticket.cache;

import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;

import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng bộ nhớ đệm cho vé và mã QR (LIEM-05).
 */
public interface TicketCache {
    Optional<TicketDetailDto> getTicketDetail(UUID ticketId);
    void putTicketDetail(TicketDetailDto ticket);

    Optional<byte[]> getTicketQrPng(UUID ticketId, long version);
    void putTicketQrPng(UUID ticketId, long version, byte[] pngBytes);

    void invalidate(UUID ticketId);
}
