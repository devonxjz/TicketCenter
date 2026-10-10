package vn.ticketscenter.settlement.commission.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.ticketscenter.settlement.commission.CommissionRuleException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CommissionRuleDtoTest {
    private static final UUID RULE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID ORGANIZATION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-10-01T00:00:00Z");

    @Test
    void preservesValidatedProjection() {
        var rule = new CommissionRuleDto(RULE_ID, ORGANIZATION_ID,
                new BigDecimal("5.00000"), Long.MAX_VALUE, FROM, TO, Long.MAX_VALUE);

        assertAll(
                () -> assertEquals(RULE_ID, rule.ruleId()),
                () -> assertEquals(ORGANIZATION_ID, rule.organizationId()),
                () -> assertEquals(new BigDecimal("5.0000"), rule.ratePercent()),
                () -> assertEquals(Long.MAX_VALUE, rule.fixedFee()),
                () -> assertEquals(FROM, rule.effectiveFrom()),
                () -> assertEquals(TO, rule.effectiveTo()),
                () -> assertEquals(Long.MAX_VALUE, rule.associatedEventsCount()));
    }

    @ParameterizedTest
    @MethodSource("invalidProjection")
    void rejectsInvalidProjection(UUID ruleId, UUID organizationId, long count) {
        assertEquals("COMMISSION_RULE_INVALID", assertThrows(CommissionRuleException.class,
                () -> new CommissionRuleDto(ruleId, organizationId,
                        new BigDecimal("5"), 0L, FROM, TO, count)).code());
    }

    static Stream<Arguments> invalidProjection() {
        return Stream.of(
                Arguments.of(null, ORGANIZATION_ID, 0L),
                Arguments.of(RULE_ID, null, 0L),
                Arguments.of(RULE_ID, ORGANIZATION_ID, -1L));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTerms")
    void rejectsInvalidTerms(String reason, BigDecimal rate, long fee, Instant from, Instant to) {
        assertEquals("COMMISSION_RULE_INVALID", assertThrows(CommissionRuleException.class,
                () -> new CommissionRuleDto(RULE_ID, ORGANIZATION_ID, rate, fee, from, to, 0L)).code());
    }

    static Stream<Arguments> invalidTerms() {
        return CommissionRuleCreateDtoTest.invalidTerms();
    }

    @ParameterizedTest
    @MethodSource("validityBoundaries")
    void usesHalfOpenValidityInterval(Instant now, boolean expected) {
        var rule = new CommissionRuleDto(RULE_ID, ORGANIZATION_ID,
                new BigDecimal("5"), 0L, FROM, TO, 0L);

        assertEquals(expected, rule.isEffectiveAt(now));
    }

    static Stream<Arguments> validityBoundaries() {
        return Stream.of(
                Arguments.of(Instant.parse("2026-09-30T23:59:59.999999999Z"), false),
                Arguments.of(Instant.parse("2026-10-01T00:00:00Z"), true),
                Arguments.of(Instant.parse("2027-01-01T00:00:00Z"), true),
                Arguments.of(Instant.parse("2027-10-01T00:00:00Z"), false),
                Arguments.of(Instant.parse("2027-10-01T00:00:00.000000001Z"), false));
    }

    @Test
    void requiresReferenceTime() {
        var rule = new CommissionRuleDto(RULE_ID, ORGANIZATION_ID,
                new BigDecimal("5"), 0L, FROM, TO, 0L);

        assertThrows(NullPointerException.class, () -> rule.isEffectiveAt(null));
    }

    @Test
    void preservesNanosecondBoundaries() {
        var rule = new CommissionRuleDto(RULE_ID, ORGANIZATION_ID,
                new BigDecimal("5"), 0L, FROM, FROM.plusNanos(1), 0L);

        assertFalse(rule.isEffectiveAt(FROM.minusNanos(1)));
        assertTrue(rule.isEffectiveAt(FROM));
        assertFalse(rule.isEffectiveAt(FROM.plusNanos(1)));
    }
}
