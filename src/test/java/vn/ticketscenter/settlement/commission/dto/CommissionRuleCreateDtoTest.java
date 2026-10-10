package vn.ticketscenter.settlement.commission.dto;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import vn.ticketscenter.settlement.commission.CommissionRuleException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class CommissionRuleCreateDtoTest {
    private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2027-10-01T00:00:00Z");

    @ParameterizedTest
    @MethodSource("validTerms")
    void acceptsAndNormalizesValidTerms(String inputRate, long fee, String expectedRate) {
        var terms = new CommissionRuleCreateDto(new BigDecimal(inputRate), fee, FROM, TO);

        assertAll(
                () -> assertEquals(new BigDecimal(expectedRate), terms.ratePercent()),
                () -> assertEquals(fee, terms.fixedFee()),
                () -> assertEquals(FROM, terms.effectiveFrom()),
                () -> assertEquals(TO, terms.effectiveTo()));
    }

    static Stream<Arguments> validTerms() {
        return Stream.of(
                Arguments.of("0", 0L, "0.0000"),
                Arguments.of("5.00000", 10000L, "5.0000"),
                Arguments.of("100", Long.MAX_VALUE, "100.0000"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTerms")
    void rejectsInvalidTerms(String reason, BigDecimal rate, long fee, Instant from, Instant to) {
        var error = assertThrows(CommissionRuleException.class,
                () -> new CommissionRuleCreateDto(rate, fee, from, to));

        assertEquals("COMMISSION_RULE_INVALID", error.code());
        assertNotNull(error.getMessage());
        if (rate != null) {
            assertFalse(error.getMessage().contains(rate.toString()), "Must not echo input rate");
        }
        if (fee < 0) {
            assertFalse(error.getMessage().contains(Long.toString(fee)), "Must not echo input fee");
        }
    }

    // Each fixture catches a missing domain guard; reused by the response DTO tests.
    static Stream<Arguments> invalidTerms() {
        BigDecimal validRate = new BigDecimal("5");
        return Stream.of(
                Arguments.of("null rate", null, 0L, FROM, TO),
                Arguments.of("negative rate", new BigDecimal("-0.0001"), 0L, FROM, TO),
                Arguments.of("rate over 100", new BigDecimal("100.0001"), 0L, FROM, TO),
                Arguments.of("inexact scale", new BigDecimal("5.00001"), 0L, FROM, TO),
                Arguments.of("enormous rate", new BigDecimal("1E+10000"), 0L, FROM, TO),
                Arguments.of("negative fee", validRate, -1L, FROM, TO),
                Arguments.of("null start", validRate, 0L, null, TO),
                Arguments.of("null end", validRate, 0L, FROM, null),
                Arguments.of("equal endpoints", validRate, 0L, FROM, FROM),
                Arguments.of("reversed endpoints", validRate, 0L, TO, FROM));
    }

    @Test
    void normalizesZeroWithExtremeScale() {
        var terms = new CommissionRuleCreateDto(
                new BigDecimal(BigInteger.ZERO, Integer.MIN_VALUE), 0L, FROM, TO);

        assertEquals(new BigDecimal("0.0000"), terms.ratePercent());
    }
}
