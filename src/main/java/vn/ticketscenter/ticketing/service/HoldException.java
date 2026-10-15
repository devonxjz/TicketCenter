package vn.ticketscenter.ticketing.service;

import vn.ticketscenter.config.error.AppException;

public final class HoldException extends AppException {
    public HoldException(int status, String code, String message) {
        super(status, code, message);
    }

    public static HoldException unavailable() {
        return new HoldException(503, "HOLD_UNAVAILABLE", "Hold storage is unavailable. Retry with the same request key.");
    }
}
