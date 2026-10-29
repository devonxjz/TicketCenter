package vn.ticketscenter.settlement.payout;

import java.util.Set;
import java.util.UUID;
import vn.ticketscenter.settlement.service.FinanceException;

@FunctionalInterface
public interface PayoutAdapter {
    Result transfer(UUID payoutId, PayoutRequestDto terms) throws Exception;

    default void requireAvailable() {}

    default Result lookup(UUID payoutId) throws Exception {
        throw new FinanceException("PAYOUT_PROVIDER_UNAVAILABLE", 503);
    }

    record Result(String status, String reference) {
        public Result {
            if (!Set.of("SUCCEEDED", "FAILED", "UNKNOWN").contains(status)) {
                throw new IllegalArgumentException("Invalid verified transfer result");
            }
            if (reference != null && (!reference.matches("[A-Za-z0-9_-]{1,100}"))) {
                throw new IllegalArgumentException("Invalid provider reference");
            }
        }
    }

    static PayoutAdapter unavailable() {
        return new PayoutAdapter() {
            public void requireAvailable() {
                throw new FinanceException("PAYOUT_PROVIDER_UNAVAILABLE", 503);
            }

            public Result transfer(UUID id, PayoutRequestDto terms) {
                requireAvailable();
                throw new IllegalStateException();
            }
        };
    }
}
