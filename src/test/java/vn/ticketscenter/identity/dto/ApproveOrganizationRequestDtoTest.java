package vn.ticketscenter.identity.dto;

import org.junit.jupiter.api.Test;
import vn.ticketscenter.identity.repository.OrganizationDecisionRepository.CommissionPolicy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApproveOrganizationRequestDtoTest {

    private static String messageOf(Map<String, String> body) {
        return assertThrows(IllegalArgumentException.class, () -> ApproveOrganizationRequestDto.from(body))
                .getMessage();
    }

    @Test
    void emptyBodyUsesTheDefaults() {
        Instant before = Instant.now();

        ApproveOrganizationRequestDto dto = ApproveOrganizationRequestDto.from(Map.of());

        assertEquals(new BigDecimal("5.0"), dto.ratePercent());
        assertEquals(10_000L, dto.fixedFee());
        assertTrue(!dto.effectiveFrom().isBefore(before));
        assertNull(dto.effectiveTo());
        CommissionPolicy policy = dto.commissionPolicy();
        assertEquals(Duration.ofDays(3650), Duration.between(policy.effectiveFrom(), policy.effectiveTo()));
    }

    @Test
    void readsFlatFieldsPercentSignAndDates() {
        ApproveOrganizationRequestDto dto = ApproveOrganizationRequestDto.from(Map.of(
                "ratePercent", " 7.5% ", "fixedFee", "15000", "effectiveFrom", "2026-11-01",
                "effectiveTo", "2027-11-01T00:00:00Z"));

        assertEquals(new BigDecimal("7.5"), dto.ratePercent());
        assertEquals(15_000L, dto.fixedFee());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), dto.effectiveFrom());
        assertEquals(Instant.parse("2027-11-01T00:00:00Z"), dto.effectiveTo());
    }

    @Test
    void effectiveDateIsAnAliasAndNestedInitialPolicyIsMerged() {
        ApproveOrganizationRequestDto dto = ApproveOrganizationRequestDto.from(Map.of(
                "effectiveDate", "2026-11-01",
                "initialPolicy", "{\"ratePercent\":\"2\",\"fixedFee\":\"500\"}"));

        assertEquals(new BigDecimal("2"), dto.ratePercent());
        assertEquals(500L, dto.fixedFee());
        assertEquals(Instant.parse("2026-10-31T17:00:00Z"), dto.effectiveFrom());
    }

    @Test
    void rejectsInvalidValuesWithTheClientMessages() {
        assertEquals("Tỷ lệ hoa hồng không hợp lệ", messageOf(Map.of("ratePercent", "abc")));
        assertEquals("Tỷ lệ hoa hồng phải từ 0% đến 100%", messageOf(Map.of("ratePercent", "101")));
        assertEquals("Tỷ lệ hoa hồng phải từ 0% đến 100%", messageOf(Map.of("ratePercent", "-1")));
        assertEquals("Phí cố định không hợp lệ", messageOf(Map.of("fixedFee", "-5")));
        assertEquals("Phí cố định không hợp lệ", messageOf(Map.of("fixedFee", "lots")));
        assertEquals("Ngày hiệu lực không hợp lệ", messageOf(Map.of("effectiveFrom", "tomorrow")));
        assertEquals("Ngày kết thúc hiệu lực không hợp lệ", messageOf(Map.of("effectiveTo", "never")));
    }

    @Test
    void policyMustStartBeforeItEnds() {
        assertEquals("Ngày bắt đầu phải trước ngày kết thúc", messageOf(Map.of(
                "effectiveFrom", "2026-11-01", "effectiveTo", "2026-11-01")));
    }
}
