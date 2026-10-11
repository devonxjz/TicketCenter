package vn.ticketscenter.fulfillment.ticket.service;

import java.util.Objects;

/**
 * Ngoại lệ nghiệp vụ cho miền vé (LIEM-05).
 */
public final class TicketException extends RuntimeException {
    private final int httpStatus;
    private final String errorCode;

    public TicketException(int httpStatus, String errorCode, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode is required");
    }

    public int httpStatus() { return httpStatus; }
    public String errorCode() { return errorCode; }

    public static TicketException unauthenticated() {
        return new TicketException(401, "UNAUTHENTICATED", "A current session is required.");
    }

    public static TicketException forbidden() {
        return new TicketException(403, "FORBIDDEN", "You do not have permission to access this ticket.");
    }

    public static TicketException notFound() {
        return new TicketException(404, "TICKET_NOT_FOUND", "The requested ticket was not found.");
    }

    public static TicketException badRequest(String message) {
        return new TicketException(400, "BAD_REQUEST", message);
    }

    public static TicketException unavailable() {
        return new TicketException(503, "SERVICE_UNAVAILABLE", "Ticket service is temporarily unavailable.");
    }

    public String toJson() {
        return "{\"error\":\"" + errorCode + "\",\"message\":\"" + getMessage() + "\"}";
    }
}
