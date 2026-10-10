package vn.ticketscenter.fulfillment.checkin;

/** Public, stable errors; database/provider details must never reach the client. */
public final class CheckInException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    public CheckInException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int httpStatus() { return httpStatus; }
    public String code() { return code; }
}
