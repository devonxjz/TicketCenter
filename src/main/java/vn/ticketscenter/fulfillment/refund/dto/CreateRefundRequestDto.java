package vn.ticketscenter.fulfillment.refund.dto;

import java.util.*;
import vn.ticketscenter.fulfillment.refund.service.RefundException;

public record CreateRefundRequestDto(UUID orderId, List<UUID> ticketIds, String reason) {
    public CreateRefundRequestDto {
        if(orderId==null || ticketIds==null || ticketIds.isEmpty() || ticketIds.size()>8
                || ticketIds.stream().anyMatch(Objects::isNull) || new HashSet<>(ticketIds).size()!=ticketIds.size()
                || reason==null) throw RefundException.invalid();
        reason=reason.strip();
        if(reason.codePointCount(0,reason.length())<10 || reason.length()>500)
            throw RefundException.invalid();
        ticketIds=ticketIds.stream().sorted(Comparator.comparing(UUID::toString)).toList();
    }
}
