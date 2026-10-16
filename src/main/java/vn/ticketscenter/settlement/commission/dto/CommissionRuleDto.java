package vn.ticketscenter.settlement.commission.dto;

import vn.ticketscenter.settlement.commission.CommissionRuleException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Value-only projection; effectiveness is evaluated at the server's reference time. */
public record CommissionRuleDto(
        UUID ruleId,
        UUID organizationId,
        BigDecimal ratePercent,
        long fixedFee,
        Instant effectiveFrom,
        Instant effectiveTo,
        long associatedEventsCount) {
    public CommissionRuleDto {
        if (ruleId == null || organizationId == null) {
            throw new CommissionRuleException(
                    "COMMISSION_RULE_INVALID", "Rule and organization IDs are required.");
        }
        if (associatedEventsCount < 0) {
            throw new CommissionRuleException(
                    "COMMISSION_RULE_INVALID", "Associated event count must be nonnegative.");
        }
        var terms = new CommissionRuleCreateDto(ratePercent, fixedFee, effectiveFrom, effectiveTo);
        ratePercent = terms.ratePercent();
    }

    public boolean isEffectiveAt(Instant now) {
        Objects.requireNonNull(now, "Reference time is required.");
        return !now.isBefore(effectiveFrom) && now.isBefore(effectiveTo);
    }
}
