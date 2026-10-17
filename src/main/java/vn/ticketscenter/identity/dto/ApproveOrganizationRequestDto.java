package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.JsonObjectParser;
import vn.ticketscenter.identity.dao.OrganizationDecisionRepository.CommissionPolicy;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

/**
 * The commission policy an admin approves an organisation with, read from the request body. Fields may also
 * arrive nested under {@code initialPolicy}. Missing values default to 5 %, a 10 000 VND fixed fee and
 * "effective now for ten years".
 */
public record ApproveOrganizationRequestDto(
        BigDecimal ratePercent, long fixedFee, Instant effectiveFrom, Instant effectiveTo) {

    private static final BigDecimal DEFAULT_RATE = new BigDecimal("5.0");
    private static final long DEFAULT_FIXED_FEE = 10_000L;
    private static final Duration DEFAULT_TERM = Duration.ofDays(3650);
    private static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** @throws IllegalArgumentException with the client-facing message when a field is invalid */
    public static ApproveOrganizationRequestDto from(Map<String, String> body) {
        Map<String, String> fields = withNestedPolicy(body);
        ApproveOrganizationRequestDto dto =
                new ApproveOrganizationRequestDto(
                        rate(fields.get("ratePercent")),
                        fixedFee(fields.get("fixedFee")),
                        instant(
                                firstNonBlank(
                                        fields.get("effectiveFrom"), fields.get("effectiveDate")),
                                "Ngày hiệu lực không hợp lệ",
                                Instant.now()),
                        instant(
                                fields.get("effectiveTo"),
                                "Ngày kết thúc hiệu lực không hợp lệ",
                                null));
        dto.validate();
        return dto;
    }

    /** @throws IllegalArgumentException when the policy ends before it starts */
    public void validate() {
        if (effectiveTo != null && !effectiveFrom.isBefore(effectiveTo)) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước ngày kết thúc");
        }
    }

    public CommissionPolicy commissionPolicy() {
        Instant to = effectiveTo != null ? effectiveTo : effectiveFrom.plus(DEFAULT_TERM);
        return new CommissionPolicy(ratePercent, fixedFee, effectiveFrom, to);
    }

    private static Map<String, String> withNestedPolicy(Map<String, String> body) {
        String nested = body.get("initialPolicy");
        if (nested == null) {
            return body;
        }
        try {
            Map<String, String> merged = new HashMap<>(body);
            merged.putAll(JsonObjectParser.parse(new StringReader(nested)));
            return merged;
        } catch (Exception unreadable) {
            return body;
        }
    }

    private static BigDecimal rate(String raw) {
        if (raw == null) {
            return DEFAULT_RATE;
        }
        BigDecimal rate;
        try {
            rate = new BigDecimal(raw.replace("%", "").trim());
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Tỷ lệ hoa hồng không hợp lệ");
        }
        if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Tỷ lệ hoa hồng phải từ 0% đến 100%");
        }
        return rate;
    }

    private static long fixedFee(String raw) {
        if (raw == null) {
            return DEFAULT_FIXED_FEE;
        }
        long fee;
        try {
            fee = new BigDecimal(raw.trim()).longValue();
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Phí cố định không hợp lệ");
        }
        if (fee < 0) {
            throw new IllegalArgumentException("Phí cố định không hợp lệ");
        }
        return fee;
    }

    private static Instant instant(String raw, String invalidMessage, Instant absent) {
        if (raw == null || raw.isBlank()) {
            return absent;
        }
        String value = raw.trim();
        try {
            return value.contains("T")
                    ? Instant.parse(value)
                    : LocalDate.parse(value).atStartOfDay(LOCAL_ZONE).toInstant();
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException(invalidMessage);
        }
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
