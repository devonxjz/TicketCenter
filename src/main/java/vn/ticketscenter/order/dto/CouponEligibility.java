package vn.ticketscenter.order.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A read-only preview for a new reservation. It does not reserve quota or authorize writes. */
public sealed interface CouponEligibility {
    record Eligible(BigDecimal discountAmount, BigDecimal totalAmount,
                    long remainingUses, Instant validUntil) implements CouponEligibility {
        public Eligible {
            Objects.requireNonNull(validUntil, "validUntil");
            Objects.requireNonNull(discountAmount, "discountAmount");
            Objects.requireNonNull(totalAmount, "totalAmount");
            var totals = new OrderSnapshot.Totals(discountAmount.add(totalAmount), discountAmount, totalAmount);
            discountAmount = totals.discountAmount();
            totalAmount = totals.totalAmount();
            if (remainingUses <= 0) throw new IllegalArgumentException("Invalid remaining quota");
        }
    }

    record Ineligible(Reason reason) implements CouponEligibility {
        public Ineligible { Objects.requireNonNull(reason, "reason"); }
    }

    enum Reason { WRONG_ORGANIZATION, INACTIVE, NOT_STARTED, EXPIRED, QUOTA_EXHAUSTED }
}
