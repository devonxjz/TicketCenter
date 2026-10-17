package vn.ticketscenter.settlement.payout;

import java.util.Map;
import java.util.UUID;
import java.sql.SQLException;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface PayoutStore {
    Map<String, Object> existing(
            UserPrincipal actor, UUID settlementId, UUID payoutId, PayoutRequestDto request)
            throws SQLException;

    Map<String, Object> reserve(
            UserPrincipal actor, UUID settlementId, UUID payoutId, PayoutRequestDto request)
            throws SQLException;

    Map<String, Object> record(
            UserPrincipal actor,
            UUID settlementId,
            UUID payoutId,
            PayoutRequestDto request,
            PayoutAdapter.Result result)
            throws SQLException;
}
