package vn.ticketscenter.ticketing.service;

public final class HoldException extends RuntimeException {
    private final int status;
    private final String code;

    public HoldException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() { return status; }
    public String code() { return code; }

    public static HoldException unavailable() {
        return new HoldException(503, "HOLD_UNAVAILABLE", "Hold storage is unavailable. Retry with the same request key.");
    }
}
