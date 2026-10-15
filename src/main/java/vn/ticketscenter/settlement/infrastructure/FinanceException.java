package vn.ticketscenter.settlement.infrastructure;

import vn.ticketscenter.config.error.AppException;

public final class FinanceException extends AppException {
    public FinanceException(String code, int status) {
        super(status, code, code);
    }
}
