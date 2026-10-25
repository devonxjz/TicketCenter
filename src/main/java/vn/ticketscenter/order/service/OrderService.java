package vn.ticketscenter.order.service;

import vn.ticketscenter.order.dto.*;
import vn.ticketscenter.order.dao.OrderRepository;
import java.util.UUID;

public interface OrderService {
    OrderMutationReceipt mutate(OrderActor actor, OrderCommand command);

    CurrentCheckout checkout(OrderActor actor, UUID holdId);

    String eligibleCoupons(OrderActor actor, UUID holdId);

    String eligibility(OrderActor actor, UUID orderId, String couponCode);

    OrderRepository.OrderHistoryPage orderHistory(
            OrderActor actor, int page, int size, String status);

    OrderDetailDto orderDetail(OrderActor actor, UUID orderId);
}
