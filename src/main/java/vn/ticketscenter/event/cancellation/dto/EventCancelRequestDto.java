package vn.ticketscenter.event.cancellation.dto;

import vn.ticketscenter.fulfillment.refund.service.RefundException;

public record EventCancelRequestDto(String reason) {
    public EventCancelRequestDto {
        if (reason == null) throw invalid();
        reason = reason.strip();
        if (reason.length() < 10 || reason.length() > 500 || reason.indexOf('\0') >= 0)
            throw invalid();
    }

    private static RefundException invalid() {
        return new RefundException(
                400, "CANCELLATION_INVALID", "Lý do hủy sự kiện phải có từ 10 đến 500 ký tự.");
    }
}
