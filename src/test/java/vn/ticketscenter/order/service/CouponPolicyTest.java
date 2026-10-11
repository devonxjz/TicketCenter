package vn.ticketscenter.order.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.ticketscenter.order.dto.CouponEligibility;
import static org.junit.jupiter.api.Assertions.*;

class CouponPolicyTest {
    private static final UUID COUPON = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID ORG = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant START = Instant.parse("2026-10-10T00:00:00Z");
    private static final Instant END = START.plusSeconds(3600);

    @ParameterizedTest @CsvSource({"0,30,0", "1,30,0", "101,30,30", "999,12.5,124",
            "1200.00,20,240", "9999999999999999999,30,2999999999999999999"})
    void percentageFloorsWholeVndExactly(String subtotal, String percent, String expected) {
        assertEquals(new BigDecimal(expected), CouponPolicy.discount(new BigDecimal(subtotal),
                new CouponPolicy.DiscountTerms.Percentage(new BigDecimal(percent))));
    }

    @ParameterizedTest @CsvSource({"0,999,0", "1,999,0", "101,999,30", "1000,299,299",
            "1000,300.00,300", "9999999999999999999,9999999999999999999,2999999999999999999"})
    void fixedDiscountRespectsThirtyPercentCap(String subtotal, String fixed, String expected) {
        assertEquals(new BigDecimal(expected), CouponPolicy.discount(new BigDecimal(subtotal),
                new CouponPolicy.DiscountTerms.FixedAmount(new BigDecimal(fixed))));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "30.0001", "50"})
    void invalidPercentageIsAConfigurationError(String percent) {
        assertThrows(IllegalArgumentException.class,
                () -> new CouponPolicy.DiscountTerms.Percentage(new BigDecimal(percent)));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.01", "10000000000000000000"})
    void invalidFixedAmountIsAConfigurationError(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> new CouponPolicy.DiscountTerms.FixedAmount(new BigDecimal(amount)));
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.01", "10000000000000000000"})
    void rejectsInvalidSubtotalBeforePreview(String subtotal) {
        assertThrows(IllegalArgumentException.class,
                () -> CouponPolicy.discount(new BigDecimal(subtotal), terms()));
        assertThrows(IllegalArgumentException.class,
                () -> CouponPolicy.preview(ORG, new BigDecimal(subtotal), facts(ORG, true, 10, 1, 1), START));
    }

    @Test void previewIncludesStartAndExcludesEnd() {
        assertEquals(new CouponEligibility.Eligible(new BigDecimal("30"), new BigDecimal("71"), 8, END),
                CouponPolicy.preview(ORG, new BigDecimal("101"), facts(ORG, true, 10, 1, 1), START));
        assertEquals(new CouponEligibility.Eligible(new BigDecimal("30"), new BigDecimal("71"), 8, END),
                CouponPolicy.preview(ORG, new BigDecimal("101"), facts(ORG, true, 10, 1, 1), END.minusNanos(1)));
        assertDenied(CouponEligibility.Reason.NOT_STARTED, facts(ORG, true, 10, 1, 1), START.minusNanos(1));
        assertDenied(CouponEligibility.Reason.EXPIRED, facts(ORG, true, 10, 1, 1), END);
    }

    @Test void deniesEachIneligibleConditionAndKeepsReservedCounts() {
        assertDenied(CouponEligibility.Reason.WRONG_ORGANIZATION, facts(COUPON, true, 10, 0, 0), START);
        assertDenied(CouponEligibility.Reason.INACTIVE, facts(ORG, false, 10, 0, 0), START);
        assertDenied(CouponEligibility.Reason.QUOTA_EXHAUSTED, facts(ORG, true, 10, 9, 1), START.plusSeconds(3599));
        assertDenied(CouponEligibility.Reason.QUOTA_EXHAUSTED, facts(ORG, true, 10, 0, 10), START);
        assertEquals(new CouponEligibility.Eligible(new BigDecimal("30"), new BigDecimal("71"), 1, END),
                CouponPolicy.preview(ORG, new BigDecimal("101"), facts(ORG, true, 10, 8, 1), START));
    }

    @Test void supportsZeroSubtotalAndOverflowSafeQuota() {
        assertEquals(new CouponEligibility.Eligible(BigDecimal.ZERO, BigDecimal.ZERO, 1, END),
                CouponPolicy.preview(ORG, BigDecimal.ZERO,
                        facts(ORG, true, Long.MAX_VALUE, Long.MAX_VALUE - 2, 1), START));
        assertThrows(IllegalArgumentException.class,
                () -> facts(ORG, true, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> facts(ORG, true, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> facts(ORG, true, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> facts(ORG, true, -1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> facts(ORG, true, 1, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> facts(ORG, true, 1, 0, -1));
    }

    @Test void factsRejectMissingIdentityInvalidCodeAndValidity() {
        UUID zero = new UUID(0, 0);
        for (UUID invalid : new UUID[] {null, zero}) {
            assertThrows(IllegalArgumentException.class, () -> facts(invalid, true, 1, 0, 0));
            assertThrows(IllegalArgumentException.class, () -> new CouponPolicy.CouponFacts(
                    invalid, ORG, "SAVE", true, START, END, terms(), 1, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> CouponPolicy.preview(invalid, BigDecimal.ONE, facts(ORG, true, 1, 0, 0), START));
        }
        for (String invalid : new String[] {null, "", " ", "A".repeat(81), "A\n", "\uD800", "\uDC00"}) {
            assertThrows(IllegalArgumentException.class, () -> new CouponPolicy.CouponFacts(
                    COUPON, ORG, invalid, true, START, END, terms(), 1, 0, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> new CouponPolicy.CouponFacts(
                COUPON, ORG, "SAVE", true, START, START, terms(), 1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CouponPolicy.CouponFacts(
                COUPON, ORG, "SAVE", true, END, START, terms(), 1, 0, 0));
    }

    @Test void nullConfigurationNeverBecomesZeroDiscount() {
        assertThrows(NullPointerException.class, () -> CouponPolicy.discount(BigDecimal.ONE, null));
        assertThrows(NullPointerException.class, () -> CouponPolicy.discount(null, terms()));
        assertThrows(NullPointerException.class, () -> new CouponPolicy.DiscountTerms.Percentage(null));
        assertThrows(NullPointerException.class, () -> new CouponPolicy.DiscountTerms.FixedAmount(null));
        assertThrows(NullPointerException.class, () -> CouponPolicy.preview(ORG, BigDecimal.ONE, null, START));
        assertThrows(NullPointerException.class,
                () -> CouponPolicy.preview(ORG, BigDecimal.ONE, facts(ORG, true, 1, 0, 0), null));
        assertThrows(NullPointerException.class, () -> new CouponPolicy.CouponFacts(
                COUPON, ORG, "SAVE", true, null, END, terms(), 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new CouponPolicy.CouponFacts(
                COUPON, ORG, "SAVE", true, START, null, terms(), 1, 0, 0));
        assertThrows(NullPointerException.class, () -> new CouponPolicy.CouponFacts(
                COUPON, ORG, "SAVE", true, START, END, null, 1, 0, 0));
    }

    @Test void eligibilityCanonicalizesMoneyAndRejectsInvalidProjections() {
        assertEquals(new CouponEligibility.Eligible(new BigDecimal("30"), new BigDecimal("71"), 1, END),
                new CouponEligibility.Eligible(new BigDecimal("30.00"), new BigDecimal("71.00"), 1, END));
        assertThrows(IllegalArgumentException.class,
                () -> new CouponEligibility.Eligible(new BigDecimal("31"), new BigDecimal("70"), 1, END));
        assertThrows(IllegalArgumentException.class,
                () -> new CouponEligibility.Eligible(new BigDecimal("0.1"), BigDecimal.ONE, 1, END));
        assertThrows(IllegalArgumentException.class,
                () -> new CouponEligibility.Eligible(BigDecimal.ZERO, BigDecimal.ONE, 0, END));
        assertThrows(NullPointerException.class, () -> new CouponEligibility.Ineligible(null));
        assertThrows(NullPointerException.class,
                () -> new CouponEligibility.Eligible(BigDecimal.ZERO, BigDecimal.ONE, 1, null));
    }

    private static CouponPolicy.DiscountTerms terms() {
        return new CouponPolicy.DiscountTerms.Percentage(new BigDecimal("30"));
    }

    private static CouponPolicy.CouponFacts facts(UUID org, boolean active, long max, long reserved, long consumed) {
        return new CouponPolicy.CouponFacts(COUPON, org, " Save ", active, START, END, terms(), max, reserved, consumed);
    }

    private static void assertDenied(CouponEligibility.Reason reason, CouponPolicy.CouponFacts facts, Instant now) {
        assertEquals(new CouponEligibility.Ineligible(reason),
                CouponPolicy.preview(ORG, new BigDecimal("101"), facts, now));
    }
}
