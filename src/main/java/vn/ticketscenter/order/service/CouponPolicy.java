package vn.ticketscenter.order.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import vn.ticketscenter.order.dto.CouponEligibility;

public final class CouponPolicy {
    private CouponPolicy() {}

    public sealed interface DiscountTerms {
        record Percentage(BigDecimal percent) implements DiscountTerms {
            public Percentage {
                Objects.requireNonNull(percent, "percent");
                if (percent.signum() <= 0 || percent.compareTo(new BigDecimal("30")) > 0) {
                    throw new IllegalArgumentException("Percentage must be positive and at most 30");
                }
            }
        }

        record FixedAmount(BigDecimal amount) implements DiscountTerms {
            public FixedAmount {
                amount = money(amount);
                if (amount.signum() == 0) throw new IllegalArgumentException("Fixed amount must be positive");
            }
        }
    }

    public record CouponFacts(UUID couponId, UUID organizationId, String code, boolean active,
                              Instant validFrom, Instant validTo, DiscountTerms terms,
                              long maxUses, long reservedUses, long consumedUses) {
        public CouponFacts {
            requireId(couponId);
            requireId(organizationId);
            requireCode(code);
            Objects.requireNonNull(validFrom, "validFrom");
            Objects.requireNonNull(validTo, "validTo");
            Objects.requireNonNull(terms, "terms");
            if (!validFrom.isBefore(validTo)) throw new IllegalArgumentException("Invalid validity interval");
            if (maxUses <= 0 || reservedUses < 0 || consumedUses < 0
                    || consumedUses > maxUses || reservedUses > maxUses - consumedUses) {
                throw new IllegalArgumentException("Invalid quota counts");
            }
        }
    }

    public static BigDecimal discount(BigDecimal subtotal, DiscountTerms terms) {
        subtotal = money(subtotal);
        Objects.requireNonNull(terms, "terms");
        return switch (terms) {
            case DiscountTerms.Percentage percentage -> subtotal.multiply(percentage.percent())
                    .divide(new BigDecimal("100"), 0, RoundingMode.DOWN);
            case DiscountTerms.FixedAmount fixed -> fixed.amount().min(
                    subtotal.multiply(new BigDecimal("0.30")).setScale(0, RoundingMode.DOWN));
        };
    }

    /** New-reservation preview with [validFrom, validTo) boundaries. SQL must recheck under lock. */
    public static CouponEligibility preview(UUID orderOrganizationId, BigDecimal subtotal,
                                            CouponFacts facts, Instant now) {
        requireId(orderOrganizationId);
        subtotal = money(subtotal);
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(now, "now");
        if (!orderOrganizationId.equals(facts.organizationId())) return denied(CouponEligibility.Reason.WRONG_ORGANIZATION);
        if (!facts.active()) return denied(CouponEligibility.Reason.INACTIVE);
        if (now.isBefore(facts.validFrom())) return denied(CouponEligibility.Reason.NOT_STARTED);
        if (!now.isBefore(facts.validTo())) return denied(CouponEligibility.Reason.EXPIRED);
        long remaining = facts.maxUses() - facts.consumedUses() - facts.reservedUses();
        if (remaining == 0) return denied(CouponEligibility.Reason.QUOTA_EXHAUSTED);
        BigDecimal discount = discount(subtotal, facts.terms());
        return new CouponEligibility.Eligible(discount, subtotal.subtract(discount), remaining, facts.validTo());
    }

    private static CouponEligibility denied(CouponEligibility.Reason reason) {
        return new CouponEligibility.Ineligible(reason);
    }

    private static BigDecimal money(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        BigDecimal whole;
        try {
            whole = amount.setScale(0, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Fractional VND", e);
        }
        if (whole.signum() < 0 || whole.precision() > 19) {
            throw new IllegalArgumentException("Amount outside nonnegative decimal(19,0)");
        }
        return whole;
    }

    private static void requireId(UUID id) {
        if (id == null || (id.getMostSignificantBits() == 0 && id.getLeastSignificantBits() == 0)) {
            throw new IllegalArgumentException("Identity must be a nonzero UUID");
        }
    }

    private static void requireCode(String code) {
        if (code == null || code.isBlank() || code.length() > 80) {
            throw new IllegalArgumentException("Invalid coupon code");
        }
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (Character.isISOControl(c)) throw new IllegalArgumentException("Control character in coupon code");
            if (Character.isHighSurrogate(c)) {
                if (++i >= code.length() || !Character.isLowSurrogate(code.charAt(i))) {
                    throw new IllegalArgumentException("Unpaired surrogate in coupon code");
                }
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired surrogate in coupon code");
            }
        }
    }
}
