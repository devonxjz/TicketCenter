package vn.ticketscenter.fulfillment.refund.service;

import java.util.List;
import java.util.UUID;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.fulfillment.refund.dto.CreateRefundRequestDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundDetailDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundPageDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundSummaryDto;
import vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface RefundRequestService {
    List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID orderId);

    List<RefundRequestStore.OrderOption> orders(UserPrincipal actor);

    RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key);

    RefundPageDto list(UserPrincipal actor, int page, int size);

    RefundDetailDto detail(UserPrincipal actor, UUID refundId);
}
