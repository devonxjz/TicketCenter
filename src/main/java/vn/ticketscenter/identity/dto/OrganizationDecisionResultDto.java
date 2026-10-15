package vn.ticketscenter.identity.dto;

import vn.ticketscenter.config.web.HttpResponses;
import vn.ticketscenter.identity.model.OrganizationStatus;

import java.util.UUID;

/** The response to approving or rejecting an organisation request. */
public record OrganizationDecisionResultDto(OrganizationStatus status, UUID organizationId, String rejectionReason,
                                            String message, OrganizationRequestDetailDto detail) {

    private static final String APPROVED_MESSAGE = "Đã phê duyệt tổ chức thành công.";
    private static final String REJECTED_MESSAGE = "Đã từ chối yêu cầu thành lập tổ chức.";

    public static OrganizationDecisionResultDto approved(UUID organizationId, OrganizationRequestDetailDto detail) {
        return new OrganizationDecisionResultDto(OrganizationStatus.APPROVED, organizationId, null,
                APPROVED_MESSAGE, detail);
    }

    public static OrganizationDecisionResultDto rejected(UUID organizationId, String reason,
                                                         OrganizationRequestDetailDto detail) {
        return new OrganizationDecisionResultDto(OrganizationStatus.REJECTED, organizationId, reason,
                REJECTED_MESSAGE, detail);
    }

    public String toJson() {
        StringBuilder json = new StringBuilder("{\"success\":true,\"status\":")
                .append(HttpResponses.jsonString(status.name()))
                .append(",\"organizationId\":").append(HttpResponses.jsonString(organizationId.toString()));
        if (status == OrganizationStatus.APPROVED) {
            json.append(",\"assignedManagerId\":").append(nullableId(detail.requesterId()))
                    .append(",\"commissionRuleId\":").append(nullableId(detail.commissionRuleId()));
        } else {
            json.append(",\"rejectionReason\":").append(HttpResponses.jsonString(rejectionReason));
        }
        return json.append(",\"message\":").append(HttpResponses.jsonString(message))
                .append(",\"detail\":").append(detail.toJson()).append('}').toString();
    }

    private static String nullableId(UUID id) {
        return HttpResponses.jsonString(id == null ? null : id.toString());
    }
}
