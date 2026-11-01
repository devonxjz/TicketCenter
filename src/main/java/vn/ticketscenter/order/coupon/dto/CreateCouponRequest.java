package vn.ticketscenter.order.coupon.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * Request to create a coupon (LIEM-06).
 */
public record CreateCouponRequest(
        String code,
        String discountType,
        BigDecimal percentageValue,
        BigDecimal fixedAmount,
        BigDecimal maxDiscountAmount,
        int maxUses,
        Instant validFrom,
        Instant validTo) {
    public CreateCouponRequest {
        Objects.requireNonNull(code, "code is required");
        Objects.requireNonNull(discountType, "discountType is required");
        Objects.requireNonNull(validFrom, "validFrom is required");
        Objects.requireNonNull(validTo, "validTo is required");
    }

    public static CreateCouponRequest fromJson(String jsonStr) {
        JSONObject obj = new JSONObject(jsonStr);
        String code = obj.getString("code").trim().toUpperCase();
        String discountType = obj.getString("discountType").trim().toUpperCase();

        BigDecimal percentageValue =
                obj.has("percentageValue") && !obj.isNull("percentageValue")
                        ? obj.getBigDecimal("percentageValue")
                        : null;
        BigDecimal fixedAmount =
                obj.has("fixedAmount") && !obj.isNull("fixedAmount")
                        ? obj.getBigDecimal("fixedAmount")
                        : null;
        BigDecimal maxDiscountAmount =
                obj.has("maxDiscountAmount") && !obj.isNull("maxDiscountAmount")
                        ? obj.getBigDecimal("maxDiscountAmount")
                        : null;

        int maxUses = obj.getInt("maxUses");
        Instant validFrom = Instant.parse(obj.getString("validFrom"));
        Instant validTo = Instant.parse(obj.getString("validTo"));

        return new CreateCouponRequest(
                code,
                discountType,
                percentageValue,
                fixedAmount,
                maxDiscountAmount,
                maxUses,
                validFrom,
                validTo);
    }
}
