package vn.ticketscenter.fulfillment.ticket.service;

import vn.ticketscenter.fulfillment.ticket.cache.TicketCache;
import vn.ticketscenter.fulfillment.ticket.dto.TicketDetailDto;
import vn.ticketscenter.fulfillment.ticket.dto.TicketSummaryDto;
import vn.ticketscenter.fulfillment.ticket.repository.TicketRepository;
import vn.ticketscenter.fulfillment.ticket.repository.TicketRepository.TicketRecord;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Dịch vụ nghiệp vụ quản lý vé và phát hành mã QR bảo mật (LIEM-05).
 */
public class TicketService {
    private final TicketRepository repository;
    private final SignedTicketQrSigner qrSigner;
    private final TicketCache cache;

    public TicketService(TicketRepository repository, SignedTicketQrSigner qrSigner, TicketCache cache) {
        this.repository = Objects.requireNonNull(repository, "repository is required");
        this.qrSigner = Objects.requireNonNull(qrSigner, "qrSigner is required");
        this.cache = Objects.requireNonNull(cache, "cache is required");
    }

    protected TicketService() {
        this.repository = null;
        this.qrSigner = null;
        this.cache = null;
    }

    /**
     * Lấy danh sách vé đã mua của người dùng hiện tại (bảo vệ quyền truy cập).
     */
    public List<TicketSummaryDto> getMyTickets(UUID actorId) {
        return getMyTickets(actorId, -1);
    }

    public List<TicketSummaryDto> getMyTickets(UUID actorId, int authVersion) {
        if (actorId == null) throw TicketException.unauthenticated();
        return repository.findTicketsByBuyerId(actorId, authVersion);
    }

    /**
     * Lấy thông tin chi tiết một vé cụ thể.
     * Kiểm soát chặt chẽ:
     * - Chỉ chủ sở hữu (buyer) mới được phép xem vé.
     * - Nếu vé không tồn tại -> 404 NOT_FOUND.
     * - Nếu vé thuộc về người khác -> 403 FORBIDDEN.
     */
    public TicketDetailDto getTicketDetail(UUID actorId, UUID ticketId) {
        return getTicketDetail(actorId, -1, ticketId);
    }

    public TicketDetailDto getTicketDetail(UUID actorId, int authVersion, UUID ticketId) {
        if (actorId == null) throw TicketException.unauthenticated();
        if (ticketId == null) throw TicketException.badRequest("ticketId is required");

        // Luôn đọc từ DB để xác thực owner trước, hoặc đối chiếu quyền
        TicketRecord record = repository.findById(ticketId, actorId, authVersion)
                .orElseThrow(TicketException::notFound);

        if (!actorId.equals(record.buyerId())) {
            throw TicketException.forbidden();
        }

        // Kiểm tra cache hoặc nạp vào cache
        return cache.getTicketDetail(ticketId)
                .filter(cached -> cached.version() == record.version() && cached.status().equals(record.status()))
                .orElseGet(() -> {
            TicketDetailDto dto = record.toDetailDto();
            cache.putTicketDetail(dto);
            return dto;
        });
    }

    /**
     * Sinh và trả về mảng byte ảnh PNG mã QR cho vé.
     * - Kiểm soát quyền truy cập: Chỉ chủ sở hữu mới được lấy QR.
     * - Sử dụng Redis cache theo key `tc:ticket:qr:v1:<id>:<version>`.
     * - Sinh QR từ Envelope: base64url(payload).base64url(signature).
     */
    public byte[] getTicketQrPng(UUID actorId, UUID ticketId, int width, int height) {
        return getTicketQrPng(actorId, -1, ticketId, width, height);
    }

    public byte[] getTicketQrPng(UUID actorId, int authVersion, UUID ticketId, int width, int height) {
        if (actorId == null) throw TicketException.unauthenticated();
        if (ticketId == null) throw TicketException.badRequest("ticketId is required");

        TicketRecord record = repository.findById(ticketId, actorId, authVersion)
                .orElseThrow(TicketException::notFound);

        if (!actorId.equals(record.buyerId())) {
            throw TicketException.forbidden();
        }

        long version = record.version();
        var cachedPng = cache.getTicketQrPng(ticketId, version);
        if (cachedPng.isPresent()) {
            return cachedPng.get();
        }

        // Tạo display name cho QR canonical payload
        String displayName = record.eventTitle();
        if (record.seatLabel() != null && !record.seatLabel().isBlank()) {
            displayName += " - " + record.seatLabel();
        } else if (record.zoneName() != null && !record.zoneName().isBlank()) {
            displayName += " - " + record.zoneName();
        }

        String envelope = qrSigner.buildEnvelope(ticketId, displayName);
        byte[] pngBytes = SignedTicketQrSigner.generateQrPng(envelope, width, height);

        // Lưu vào cache với TTL hữu hạn
        cache.putTicketQrPng(ticketId, version, pngBytes);
        return pngBytes;
    }

    /**
     * Hủy bỏ cache khi trạng thái vé thay đổi (check-in, refund, v.v.).
     */
    public void invalidate(UUID ticketId) {
        cache.invalidate(ticketId);
    }
}
