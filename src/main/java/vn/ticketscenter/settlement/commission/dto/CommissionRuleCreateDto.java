package vn.ticketscenter.settlement.commission.dto;

import vn.ticketscenter.config.error.AppException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Validated terms; fixedFee is an integer amount in VND. */
public record CommissionRuleCreateDto(
        BigDecimal ratePercent, long fixedFee, Instant effectiveFrom, Instant effectiveTo) {
    public CommissionRuleCreateDto {
        if (ratePercent == null
                || ratePercent.signum() < 0
                || ratePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw invalid("ratePercent must be between zero and one hundred.");
        }
        if (fixedFee < 0) {
            throw invalid("fixedFee must be nonnegative.");
        }
        if (effectiveFrom == null || effectiveTo == null || !effectiveFrom.isBefore(effectiveTo)) {
            throw invalid("effectiveFrom must precede effectiveTo.");
        }
        // Zero may carry an extreme scale; its canonical representation is always safe.
        BigDecimal significantRate =
                ratePercent.signum() == 0 ? BigDecimal.ZERO : ratePercent.stripTrailingZeros();
        if (significantRate.scale() > 4) {
            throw invalid("ratePercent must be exactly representable with four decimal places.");
        }
        ratePercent = significantRate.setScale(4, RoundingMode.UNNECESSARY);
    }

    private static AppException invalid(String message) {
        return new AppException(400, "COMMISSION_RULE_INVALID", message);
    }
}
