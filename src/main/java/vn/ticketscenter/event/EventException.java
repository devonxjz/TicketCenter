package vn.ticketscenter.event;

import vn.ticketscenter.config.error.AppException;

public final class EventException extends AppException {

    public EventException(int status, String code, String message) {
        super(status, code, message);
    }
}
