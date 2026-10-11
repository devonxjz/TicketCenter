package vn.ticketscenter.order.dto;

import java.util.UUID;

public sealed interface CouponMutation {
    UUID orderId();

    record SetCoupon(UUID orderId, String couponCode) implements CouponMutation {
        public SetCoupon {
            OrderRequests.requireId(orderId);
            OrderRequests.requireText(couponCode, 80);
        }
    }

    record RemoveCoupon(UUID orderId) implements CouponMutation {
        public RemoveCoupon {
            OrderRequests.requireId(orderId);
        }
    }
}
