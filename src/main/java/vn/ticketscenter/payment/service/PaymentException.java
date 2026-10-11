package vn.ticketscenter.payment.service;

import java.util.Objects;

public final class PaymentException extends RuntimeException {
    private final int status;
    private final String code;

    public PaymentException(int status, String code, String message) {
        super(Objects.requireNonNull(message, "message is required"));
        if (status < 400 || status > 599) {
            throw new IllegalArgumentException("invalid HTTP status: " + status);
        }
        this.status = status;
        this.code = Objects.requireNonNull(code, "code is required");
    }

    public int status() { return status; }
    public String code() { return code; }

    public static PaymentException unauthenticated() {
        return new PaymentException(401, "UNAUTHENTICATED", "A current session is required.");
    }

    public static PaymentException forbidden() {
        return new PaymentException(403, "FORBIDDEN", "A current verified owner is required.");
    }

    public static PaymentException orderNotFound() {
        return new PaymentException(404, "ORDER_NOT_FOUND", "Order was not found.");
    }

    public static PaymentException orderNotPayable() {
        return new PaymentException(400, "ORDER_NOT_PAYABLE", "Order is not in a payable state.");
    }

    public static PaymentException eventUnavailable() {
        return new PaymentException(400, "EVENT_UNAVAILABLE", "Event is unavailable for payment.");
    }

    public static PaymentException paymentUnresolved() {
        return new PaymentException(409, "PAYMENT_UNRESOLVED", "Payment is already in progress or unresolved.");
    }

    public static PaymentException idempotencyConflict() {
        return new PaymentException(409, "IDEMPOTENCY_CONFLICT", "A different request was already sent with this key.");
    }

    public static PaymentException unavailable() {
        return new PaymentException(503, "SERVICE_UNAVAILABLE", "Payment service is temporarily unavailable.");
    }
}
