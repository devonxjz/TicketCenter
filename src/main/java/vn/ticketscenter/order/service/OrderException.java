package vn.ticketscenter.order.service;

public final class OrderException extends RuntimeException {
    private final int status;
    private final String code;
    public OrderException(int status, String code, String message) {
        super(message); this.status = status; this.code = code;
    }
    public int status() { return status; }
    public String code() { return code; }
    public static OrderException unavailable() { return new OrderException(503, "ORDER_UNAVAILABLE", "Refresh checkout and retry the same request."); }
}
