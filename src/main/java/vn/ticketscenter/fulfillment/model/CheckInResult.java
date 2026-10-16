package vn.ticketscenter.fulfillment.model;

public enum CheckInResult {
    SUCCESS,
    INVALID_CODE,
    WRONG_EVENT,
    ALREADY_USED,
    OUTSIDE_WINDOW,
    INACTIVE_TICKET,
    FORBIDDEN
}
