package vn.ticketscenter.order.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Thông tin quản trị Coupon kèm số liệu sử dụng thực tế (LIEM-06 / UI-14).
 */
public record CouponAdminDto(
        UUID id,
        UUID organizationId,
        String organizationName,
        String code,
        String discountType,
        BigDecimal percentageValue,
        BigDecimal fixedAmount,
        BigDecimal maxDiscountAmount,
        int maxUses,
        int reservedUses,
        int consumedUses,
        int availableUses,
        Instant validFrom,
        Instant validTo,
        boolean active,
        long version
) {
    public CouponAdminDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(discountType, "discountType is required");
    }

    public String toJson() {
        return "{\"id\":\"" + id + "\","
                + "\"organizationId\":\"" + organizationId + "\","
                + "\"organizationName\":\"" + escape(organizationName != null ? organizationName : "") + "\","
                + "\"code\":\"" + escape(code) + "\","
                + "\"discountType\":\"" + escape(discountType) + "\","
                + "\"percentageValue\":" + (percentageValue != null ? percentageValue : "null") + ","
                + "\"fixedAmount\":" + (fixedAmount != null ? fixedAmount : "null") + ","
                + "\"maxDiscountAmount\":" + (maxDiscountAmount != null ? maxDiscountAmount : "null") + ","
                + "\"maxUses\":" + maxUses + ","
                + "\"reservedUses\":" + reservedUses + ","
                + "\"consumedUses\":" + consumedUses + ","
                + "\"availableUses\":" + availableUses + ","
                + "\"validFrom\":\"" + (validFrom != null ? validFrom.toString() : "") + "\","
                + "\"validTo\":\"" + (validTo != null ? validTo.toString() : "") + "\","
                + "\"active\":" + active + ","
                + "\"version\":" + version + "}";
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
