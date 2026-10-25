package vn.ticketscenter.fulfillment.refund.service;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.dto.*;
import vn.ticketscenter.fulfillment.refund.dao.RefundRequestStore;
import vn.ticketscenter.identity.security.UserPrincipal;

public interface RefundRequestService {
    List<RefundTicketOptionDto> tickets(UserPrincipal actor, UUID order);

    List<RefundRequestStore.OrderOption> orders(UserPrincipal actor);

    RefundSummaryDto create(UserPrincipal actor, CreateRefundRequestDto request, UUID key);

    RefundPageDto list(UserPrincipal actor, int page, int size);

    RefundDetailDto detail(UserPrincipal actor, UUID id);
}
