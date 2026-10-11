package vn.ticketscenter.fulfillment.refund.dto;

import java.util.*;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record AdminRefundMutationDto(UUID refundId,String status,String canonicalStatus,UUID attemptId,
        UUID buyerId,List<UUID> ticketIds) {
    public AdminRefundMutationDto { ticketIds=List.copyOf(ticketIds); }
    public String json() { return "{\"success\":true,\"refundId\":"+text(refundId.toString())+",\"status\":"+text(status)
        +",\"canonicalStatus\":"+text(canonicalStatus)+",\"attemptId\":"+text(attemptId==null?null:attemptId.toString())
        +",\"message\":"+text("REJECTED".equals(status)?"Đã từ chối yêu cầu hoàn tiền.":"COMPLETED".equals(canonicalStatus)?"Đã hoàn tất yêu cầu hoàn vé có giá trị 0 đồng.":"Đã phê duyệt yêu cầu và đưa vào hàng đợi hoàn tiền.")+"}"; }
}
