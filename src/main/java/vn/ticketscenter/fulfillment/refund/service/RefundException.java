package vn.ticketscenter.fulfillment.refund.service;

import vn.ticketscenter.config.error.AppException;

public final class RefundException extends AppException {
    public RefundException(int status, String code, String message) { super(status, code, message); }
    public static RefundException invalid() { return new RefundException(400,"REFUND_REQUEST_INVALID","Thông tin yêu cầu hoàn vé không hợp lệ."); }
}
