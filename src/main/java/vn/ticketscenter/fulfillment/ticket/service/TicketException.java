package vn.ticketscenter.fulfillment.ticket.service;

import vn.ticketscenter.config.error.AppException;

import java.util.Objects;

/**
 * Business error of the ticket feature (LIEM-05).
 */
public final class TicketException extends AppException {

    public TicketException(int httpStatus, String errorCode, String message) {
        super(httpStatus, Objects.requireNonNull(errorCode, "errorCode is required"), message);
    }

    public static TicketException unauthenticated() {
        return new TicketException(401, "UNAUTHENTICATED", "A current session is required.");
    }

    public static TicketException forbidden() {
        return new TicketException(
                403, "FORBIDDEN", "You do not have permission to access this ticket.");
    }

    public static TicketException notFound() {
        return new TicketException(404, "TICKET_NOT_FOUND", "The requested ticket was not found.");
    }

    public static TicketException badRequest(String message) {
        return new TicketException(400, "BAD_REQUEST", message);
    }

    public static TicketException unavailable() {
        return new TicketException(
                503, "SERVICE_UNAVAILABLE", "Ticket service is temporarily unavailable.");
    }

    public String toJson() {
        return "{\"error\":\"" + code() + "\",\"message\":\"" + getMessage() + "\"}";
    }
}
