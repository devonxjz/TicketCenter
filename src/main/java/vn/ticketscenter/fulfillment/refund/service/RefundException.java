package vn.ticketscenter.fulfillment.refund.service;

public final class RefundException extends RuntimeException {
    private final int status;
    private final String code;
    public RefundException(int status, String code, String message) { super(message); this.status=status; this.code=code; }
    public int status() { return status; }
    public String code() { return code; }
    public static RefundException invalid() { return new RefundException(400,"REFUND_REQUEST_INVALID","Thông tin yêu cầu hoàn vé không hợp lệ."); }
}
