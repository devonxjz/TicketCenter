<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%-- UI-10: organisation context switcher --%>
<div class="tc-context-switch d-inline-flex align-items-center gap-2" id="tcOrgSwitcherContainer">
    <label for="tcOrgSelect" class="small text-muted d-none d-sm-inline mb-0 fw-semibold">
        <i class="fa-solid fa-building me-1"></i>Tổ chức:
    </label>
    <div class="input-group input-group-sm tc-org-switch-group">
        <select id="tcOrgSelect" class="form-select form-select-sm rounded-0 border-secondary-subtle fw-bold"
            aria-label="Chọn tổ chức đang thao tác">
            <option value="" disabled selected>Đang tải tổ chức...</option>
        </select>
        <button class="btn btn-outline-secondary rounded-0 px-2" type="button" id="tcOrgRefreshBtn"
            title="Tải lại danh sách tổ chức">
            <i class="fa-solid fa-rotate-right"></i>
        </button>
    </div>
</div>

<script defer src="${pageContext.request.contextPath}/assets/js/organization/context-switch.js"></script>
