package vn.ticketscenter.settlement.service;

import java.sql.SQLException;
import java.util.*;
import vn.ticketscenter.identity.security.UserPrincipal;
import vn.ticketscenter.settlement.commission.dto.CommissionRuleCreateDto;
import vn.ticketscenter.settlement.payout.*;

public interface FinanceMutationService {
    String readJson(UserPrincipal actor, String kind, UUID id);

    Object read(UserPrincipal actor, String kind, UUID id);

    Map<String, Object> commission(
            UserPrincipal actor, UUID org, UUID rule, UUID key, CommissionRuleCreateDto terms);

    Map<String, Object> recalculate(UserPrincipal actor, UUID event);

    Map<String, Object> confirm(UserPrincipal actor, UUID settlement);

    Map<String, Object> payout(
            UserPrincipal actor, UUID settlement, UUID key, PayoutRequestDto terms)
            throws SQLException;

    Map<String, Object> reconcile(
            UserPrincipal actor, UUID settlement, UUID payout, PayoutRequestDto terms)
            throws SQLException;
}
