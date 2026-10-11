package vn.ticketscenter.fulfillment.refund.dto;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record AdminRefundDetailDto(AdminRefundSummaryDto refund, UUID currentAttemptId, UUID reviewerId,
        Instant decidedAt, String adminNote, List<RefundTicketOptionDto> tickets, List<Attempt> attempts) {
    public AdminRefundDetailDto { tickets=List.copyOf(tickets); attempts=List.copyOf(attempts); }
    public record Attempt(UUID attemptId, int attemptNo, String status, String providerReference,
            Instant createdAt, Instant processedAt) {
        public String json() { return "{\"attemptId\":"+text(attemptId.toString())+",\"attemptNo\":"+attemptNo
            +",\"status\":"+text(status)+",\"providerReference\":"+text(providerReference)
            +",\"createdAt\":"+text(createdAt.toString())+",\"processedAt\":"+text(processedAt==null?null:processedAt.toString())+"}"; }
    }
    public String json() { return "{\"refund\":"+refund.json()+",\"currentAttemptId\":"+text(currentAttemptId==null?null:currentAttemptId.toString())
        +",\"reviewerId\":"+text(reviewerId==null?null:reviewerId.toString())+",\"decidedAt\":"+text(decidedAt==null?null:decidedAt.toString())
        +",\"adminNote\":"+text(adminNote)+",\"repaymentMethod\":\"Hoàn về phương thức thanh toán gốc\",\"tickets\":"+RefundDetailDto.ticketJson(tickets)
        +",\"attempts\":"+attempts.stream().map(Attempt::json).collect(Collectors.joining(",","[","]"))+"}"; }
}
