package vn.ticketscenter.order.coupon.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.number;
import static vn.ticketscenter.config.web.Json.object;
import static vn.ticketscenter.config.web.Json.text;

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
        long version) {
    public CouponAdminDto {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(organizationId, "organizationId is required");
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(discountType, "discountType is required");
    }

    public String toJson() {
        return object(
                field("id", text(id)),
                field("organizationId", text(organizationId)),
                field("organizationName", text(organizationName)),
                field("code", text(code)),
                field("discountType", text(discountType)),
                field("percentageValue", number(percentageValue)),
                field("fixedAmount", number(fixedAmount)),
                field("maxDiscountAmount", number(maxDiscountAmount)),
                field("maxUses", number(maxUses)),
                field("reservedUses", number(reservedUses)),
                field("consumedUses", number(consumedUses)),
                field("availableUses", number(availableUses)),
                field("validFrom", text(validFrom)),
                field("validTo", text(validTo)),
                field("active", number(active)),
                field("version", number(version)));
    }
}
