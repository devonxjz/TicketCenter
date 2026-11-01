package vn.ticketscenter.settlement.payout;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.sql.SQLException;
import vn.ticketscenter.identity.security.UserPrincipal;

/** A committed reservation precedes the provider call; replays never transfer again. */
public final class PayoutExecutionService {
    private final PayoutStore store;
    private final PayoutAdapter adapter;

    public PayoutExecutionService(PayoutStore store, PayoutAdapter adapter) {
        this.store = Objects.requireNonNull(store);
        this.adapter = Objects.requireNonNull(adapter);
    }

    public Map<String, Object> execute(
            UserPrincipal actor, UUID settlementId, UUID payoutId, PayoutRequestDto request)
            throws SQLException {
        Map<String, Object> existing = store.existing(actor, settlementId, payoutId, request);
        if (existing != null) {
            return existing;
        }
        adapter.requireAvailable();
        Map<String, Object> reserved = store.reserve(actor, settlementId, payoutId, request);
        if (Boolean.TRUE.equals(reserved.get("isReplay"))) {
            return reserved;
        }
        PayoutAdapter.Result result;
        try {
            result = Objects.requireNonNull(adapter.transfer(payoutId, request));
        } catch (Exception unavailable) {
            result = new PayoutAdapter.Result("UNKNOWN", null);
        }
        return store.record(actor, settlementId, payoutId, request, result);
    }
}
