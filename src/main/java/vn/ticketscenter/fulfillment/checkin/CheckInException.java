package vn.ticketscenter.fulfillment.checkin;

import vn.ticketscenter.config.error.AppException;

/** Public, stable errors; database/provider details must never reach the client. */
public final class CheckInException extends AppException {

    public CheckInException(int httpStatus, String code, String message) {
        super(httpStatus, code, message);
    }

    public int httpStatus() { return status(); }
}
