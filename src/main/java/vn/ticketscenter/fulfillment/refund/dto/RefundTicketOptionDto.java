package vn.ticketscenter.fulfillment.refund.dto;

import java.util.UUID;
import vn.ticketscenter.config.web.HttpResponses;

public record RefundTicketOptionDto(UUID ticketId,String ticketCode,String seatLabel,String paidAmount,
                                   String status,boolean isRefundable,String unrefundableReason) {
    public String json() {
        return "{\"ticketId\":"+text(ticketId.toString())+",\"ticketCode\":"+text(ticketCode)+",\"seatLabel\":"+text(seatLabel)
                +",\"paidAmount\":"+text(paidAmount)+",\"status\":"+text(status)+",\"isRefundable\":"+isRefundable
                +",\"unrefundableReason\":"+text(unrefundableReason)+"}";
    }
    public static String text(String value) { return value==null?"null":HttpResponses.jsonString(value); }
}
