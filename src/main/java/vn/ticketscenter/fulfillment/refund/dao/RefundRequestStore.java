package vn.ticketscenter.fulfillment.refund.dao;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface RefundRequestStore {
    record OrderOption(UUID orderId, String orderCode, String eventName) {}

    boolean active(UserPrincipal actor);

    List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID orderId);

    List<OrderOption> orders(UserPrincipal actor);

    RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key);

    RefundPageDto list(UserPrincipal actor, int page, int size);

    RefundDetailDto detail(UserPrincipal actor, UUID refundId);
}
