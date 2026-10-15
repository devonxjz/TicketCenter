package vn.ticketscenter.order.service;

import vn.ticketscenter.config.error.AppException;

public final class OrderException extends AppException {
    public OrderException(int status, String code, String message) {
        super(status, code, message);
    }
    public static OrderException unavailable() { return new OrderException(503, "ORDER_UNAVAILABLE", "Refresh checkout and retry the same request."); }
}
