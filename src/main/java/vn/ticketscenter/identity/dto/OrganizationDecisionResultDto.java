package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OrganizationStatus;

import java.util.UUID;

import static vn.ticketscenter.config.web.Json.field;
import static vn.ticketscenter.config.web.Json.nullableText;
import static vn.ticketscenter.config.web.Json.object;

/** The response to approving or rejecting an organisation request. */
public record OrganizationDecisionResultDto(
        OrganizationStatus status,
        UUID organizationId,
        String rejectionReason,
        String message,
        OrganizationRequestDetailDto detail) {

    private static final String APPROVED_MESSAGE = "Đã phê duyệt tổ chức thành công.";
    private static final String REJECTED_MESSAGE = "Đã từ chối yêu cầu thành lập tổ chức.";

    public static OrganizationDecisionResultDto approved(
            UUID organizationId, OrganizationRequestDetailDto detail) {
        return new OrganizationDecisionResultDto(
                OrganizationStatus.APPROVED, organizationId, null, APPROVED_MESSAGE, detail);
    }

    public static OrganizationDecisionResultDto rejected(
            UUID organizationId, String reason, OrganizationRequestDetailDto detail) {
        return new OrganizationDecisionResultDto(
                OrganizationStatus.REJECTED, organizationId, reason, REJECTED_MESSAGE, detail);
    }

    public String toJson() {
        boolean approved = status == OrganizationStatus.APPROVED;
        return object(
                field("success", "true"),
                field("status", nullableText(status.name())),
                field("organizationId", nullableText(organizationId.toString())),
                approved ? field("assignedManagerId", nullableText(detail.requesterId())) : null,
                approved ? field("commissionRuleId", nullableText(detail.commissionRuleId())) : null,
                approved ? null : field("rejectionReason", nullableText(rejectionReason)),
                field("message", nullableText(message)),
                field("detail", detail.toJson()));
    }
}
