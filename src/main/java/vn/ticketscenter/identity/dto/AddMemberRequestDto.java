package vn.ticketscenter.identity.dto;

import vn.ticketscenter.identity.model.OrganizationRole;

public record AddMemberRequestDto(String userName, OrganizationRole role) {
    public void validate() {
        if (userName == null || userName.isBlank()) {
            throw new IllegalArgumentException("Tên đăng nhập không được để trống");
        }
        if (role == null) {
            throw new IllegalArgumentException(
                    "Vai trò không hợp lệ: chỉ chấp nhận MANAGER hoặc CHECK_IN_STAFF");
        }
    }
}
