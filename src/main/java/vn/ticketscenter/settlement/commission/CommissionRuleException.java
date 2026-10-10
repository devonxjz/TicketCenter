package vn.ticketscenter.settlement.commission;

/** Domain failure whose code can be mapped by a future HTTP adapter. */
public final class CommissionRuleException extends RuntimeException {
    private final String code;

    public CommissionRuleException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
