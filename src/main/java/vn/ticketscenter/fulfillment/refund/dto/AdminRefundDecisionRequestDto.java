package vn.ticketscenter.fulfillment.refund.dto;

import vn.ticketscenter.fulfillment.refund.service.RefundException;

public record AdminRefundDecisionRequestDto(String decision, String note, String rejectReason) {
    public AdminRefundDecisionRequestDto {
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) throw RefundException.invalid();
        note = clean(note);
        rejectReason = clean(rejectReason);
        if (note != null && note.length() > 500 || rejectReason != null && rejectReason.length() > 1000)
            throw RefundException.invalid();
        if ("REJECT".equals(decision) && rejectReason == null)
            throw new RefundException(400, "REJECTION_REASON_REQUIRED", "Lý do từ chối không được để trống.");
        if ("APPROVE".equals(decision) && rejectReason != null) throw RefundException.invalid();
        if ("REJECT".equals(decision) && note != null) throw RefundException.invalid();
    }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.strip(); }
}
