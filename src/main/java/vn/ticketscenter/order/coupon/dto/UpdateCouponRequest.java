package vn.ticketscenter.order.coupon.dto;

import org.json.JSONObject;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Request to update a coupon (LIEM-06).
 */
public record UpdateCouponRequest(Integer maxUses, Instant validTo, BigDecimal maxDiscountAmount) {
    public static UpdateCouponRequest fromJson(String jsonStr) {
        JSONObject obj = new JSONObject(jsonStr);
        Integer maxUses =
                obj.has("maxUses") && !obj.isNull("maxUses") ? obj.getInt("maxUses") : null;
        Instant validTo =
                obj.has("validTo") && !obj.isNull("validTo")
                        ? Instant.parse(obj.getString("validTo"))
                        : null;
        BigDecimal maxDiscountAmount =
                obj.has("maxDiscountAmount") && !obj.isNull("maxDiscountAmount")
                        ? obj.getBigDecimal("maxDiscountAmount")
                        : null;

        return new UpdateCouponRequest(maxUses, validTo, maxDiscountAmount);
    }
}
