package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OrganizationRole;

public record ChangeMemberRoleRequestDto(
        OrganizationRole role
) {
    public void validate() {
        if (role == null) {
            throw new IllegalArgumentException("Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
        }
    }
}
