package vn.ticketscenter.fulfillment.ticket.service;

import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import java.util.List;
import java.util.UUID;

/**
 * Dịch vụ nghiệp vụ quản lý vé và phát hành mã QR bảo mật (LIEM-05).
 */
public interface TicketService {
    /**
     * Lấy danh sách vé đã mua của người dùng hiện tại (bảo vệ quyền truy cập).
     */
    List<TicketSummaryDto> getMyTickets(UUID actorId);

    List<TicketSummaryDto> getMyTickets(UUID actorId, int authVersion);

    /**
     * Lấy thông tin chi tiết một vé cụ thể.
     * Kiểm soát chặt chẽ:
     * - Chỉ chủ sở hữu (buyer) mới được phép xem vé.
     * - Nếu vé không tồn tại -> 404 NOT_FOUND.
     * - Nếu vé thuộc về người khác -> 403 FORBIDDEN.
     */
    TicketDetailDto getTicketDetail(UUID actorId, UUID ticketId);

    TicketDetailDto getTicketDetail(UUID actorId, int authVersion, UUID ticketId);

    /**
     * Sinh và trả về mảng byte ảnh PNG mã QR cho vé.
     * - Kiểm soát quyền truy cập: Chỉ chủ sở hữu mới được lấy QR.
     * - Sử dụng Redis cache theo key `tc:ticket:qr:v1:<id>:<version>`.
     * - Sinh QR từ Envelope: base64url(payload).base64url(signature).
     */
    byte[] getTicketQrPng(UUID actorId, UUID ticketId, int width, int height);

    byte[] getTicketQrPng(UUID actorId, int authVersion, UUID ticketId, int width, int height);

    /**
     * Hủy bỏ cache khi trạng thái vé thay đổi (check-in, refund, v.v.).
     */
    void invalidate(UUID ticketId);
}
