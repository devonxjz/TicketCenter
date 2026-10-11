package vn.ticketscenter.order.dto;

import java.time.Instant;
import java.util.Optional;

public record CurrentCheckout(String body, Instant serverTime, Optional<CurrentOrder> order) {
    public enum PaymentResolution { CLEAR, PAYMENT_PENDING, UNKNOWN }
    public enum HoldState { ACTIVE, RELEASED, CONSUMED }
    public record CurrentOrder(String body, OrderSnapshot snapshot, long version,
                               HoldState holdStatus, PaymentResolution paymentResolution, boolean canEditCoupon) { }
}
