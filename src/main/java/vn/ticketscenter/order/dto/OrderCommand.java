package vn.ticketscenter.order.dto;

import java.util.UUID;

public sealed interface OrderCommand {
    UUID key();

    UUID targetId();

    String fingerprint(UUID actorId);

    int httpStatus();

    record Create(UUID key, CreateOrderRequest request) implements OrderCommand {
        public Create {
            OrderRequests.requireId(key);
            java.util.Objects.requireNonNull(request);
        }

        public UUID targetId() {
            return request.holdId();
        }

        public String fingerprint(UUID actorId) {
            return OrderRequests.fingerprint(actorId, request);
        }

        public int httpStatus() {
            return 201;
        }
    }

    record ChangeCoupon(UUID key, CouponMutation mutation) implements OrderCommand {
        public ChangeCoupon {
            OrderRequests.requireId(key);
            java.util.Objects.requireNonNull(mutation);
        }

        public UUID targetId() {
            return mutation.orderId();
        }

        public String fingerprint(UUID actorId) {
            return OrderRequests.fingerprint(actorId, mutation);
        }

        public int httpStatus() {
            return 200;
        }
    }
}
