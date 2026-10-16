package vn.ticketscenter.fulfillment.checkin.dto;

import vn.ticketscenter.fulfillment.checkin.CheckInException;

import java.util.UUID;

public record CheckInScanRequestDto(UUID eventId, String qrContent, String ticketCode) {
    public CheckInScanRequestDto {
        if (eventId == null || (qrContent == null) == (ticketCode == null)
                || qrContent != null && (qrContent.isBlank() || qrContent.length() > 4096)
                || ticketCode != null && !ticketCode.matches("[A-Za-z0-9_-]{1,120}")) {
            throw new CheckInException(400, "INVALID_SCAN_REQUEST", "Nhập một mã QR hoặc mã vé hợp lệ và chọn sự kiện.");
        }
    }

    @Override
    public String toString() {
        return "CheckInScanRequestDto[eventId=" + eventId + ", redacted]";
    }
}
