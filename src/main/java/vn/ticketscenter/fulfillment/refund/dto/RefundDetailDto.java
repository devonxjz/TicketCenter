package vn.ticketscenter.fulfillment.refund.dto;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import static vn.ticketscenter.fulfillment.refund.dto.RefundTicketOptionDto.text;

public record RefundDetailDto(RefundSummaryDto refund,UUID currentAttemptId,Instant processedAt,
                              List<RefundTicketOptionDto> tickets,List<Attempt> attempts) {
    public RefundDetailDto { tickets=List.copyOf(tickets); attempts=List.copyOf(attempts); }
    public record Attempt(UUID attemptId,int attemptNo,String status,Instant createdAt,Instant processedAt) {
        public String json() { return "{\"attemptId\":"+text(attemptId.toString())+",\"attemptNo\":"+attemptNo+",\"status\":"+text(status)
                +",\"createdAt\":"+text(createdAt.toString())+",\"processedAt\":"+text(processedAt==null?null:processedAt.toString())+"}"; }
    }
    public String json() {
        return "{\"refund\":"+refund.json()+",\"currentAttemptId\":"+text(currentAttemptId==null?null:currentAttemptId.toString())
                +",\"processedAt\":"+text(processedAt==null?null:processedAt.toString())+",\"tickets\":"+ticketJson(tickets)
                +",\"attempts\":"+attemptJson(attempts)+"}";
    }
    public static String ticketJson(List<RefundTicketOptionDto> values) { return values.stream().map(RefundTicketOptionDto::json).collect(Collectors.joining(",","[","]")); }
    public static String attemptJson(List<Attempt> values) { return values.stream().map(Attempt::json).collect(Collectors.joining(",","[","]")); }
}
