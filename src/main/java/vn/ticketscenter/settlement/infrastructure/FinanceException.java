package vn.ticketscenter.settlement.infrastructure;

public final class FinanceException extends RuntimeException {
    private final String code;
    private final int status;
    public FinanceException(String code, int status) {
        super(code); this.code = code; this.status = status;
    }
    public String code() { return code; }
    public int status() { return status; }
}
