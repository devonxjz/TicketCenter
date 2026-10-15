package vn.ticketscenter.settlement.commission;

import vn.ticketscenter.config.error.AppException;

/** Domain failure whose code can be mapped by a future HTTP adapter. */
public final class CommissionRuleException extends AppException {

    public CommissionRuleException(String code, String message) {
        super(400, code, message);
    }

}
