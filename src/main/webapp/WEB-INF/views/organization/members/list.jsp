<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="activeTab" value="members" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Nhân sự &amp; phân quyền | TicketsCenter Partner</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <meta name="csrf-token" content="${sessionScope.TC_CSRF_TOKEN}">
    <meta name="context-path" content="${pageContext.request.contextPath}">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css">
</head>
<body class="bg-light min-h-screen tc-organizer-page">

    <!-- Top Navbar for Portal -->
    <nav class="navbar navbar-custom sticky-top">
        <div class="container-fluid px-4 d-flex align-items-center justify-content-between">
            <div class="d-flex align-items-center gap-3">
                <a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">
                    TicketsCenter
                </a>
                <span class="status-pill status-dark">Cổng Đối Tác</span>
            </div>

            <!-- Org Context Switcher (UI-10) & User info -->
            <div class="d-flex align-items-center gap-3">
                <jsp:include page="/WEB-INF/views/organization/context-switch.jsp" />
                <div class="vr mx-2"></div>
                <div class="d-flex align-items-center gap-2 small">
                    <span class="fw-bold d-none d-md-inline">
                        <c:out value="${sessionScope.TC_CURRENT_USER.fullName}" default="Thành viên" />
                    </span>
                    <a href="${pageContext.request.contextPath}/auth/logout" class="btn btn-sm btn-outline-dark rounded-0 px-3">Đăng xuất</a>
                </div>
            </div>
        </div>
    </nav>

    <!-- Dashboard Main Layout -->
    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />

        <main class="dashboard-content">
            <!-- Page Header -->
            <div class="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center pb-4 mb-4 border-bottom">
                <div>
                    <h1 class="h3 fw-bold mb-1 text-dark">Nhân Sự & Phân Quyền Tổ Chức</h1>
                    <p class="small text-muted mb-0">Quản lý danh sách thành viên tham gia vận hành tổ chức, phân quyền quản lý và soát vé sự kiện.</p>
                </div>
                <div class="mt-3 mt-sm-0 d-flex gap-2">
                    <button class="btn btn-outline-secondary rounded-0 btn-sm" id="btnReloadMembers" type="button" title="Làm mới danh sách">
                        <i class="fa-solid fa-arrows-rotate me-1"></i> Làm mới
                    </button>
                    <button class="btn-primary-custom" type="button" id="btnOpenAddMember">
                        <i class="fa-solid fa-user-plus me-1"></i> Thêm thành viên
                    </button>
                </div>
            </div>

            <!-- Dynamic Alert placeholder -->
            <div id="membersAlertPlaceholder"></div>

            <!-- Invariant Notice Banner -->
            <div class="p-3 bg-white border border-danger-subtle text-danger small mb-4 ">
                <i class="fa-solid fa-shield-halved me-2"></i> <strong>Lưu ý quản trị:</strong>
                Tổ chức cần duy trì tối thiểu 01 Quản lý đang hoạt động. Hệ thống không cho phép khóa hoặc hạ quyền tài khoản Quản lý duy nhất còn lại của tổ chức.
            </div>

            <!-- Filter & Search Toolbar -->
            <div class="row g-2 mb-4 align-items-center">
                <div class="col-md-5">
                    <div class="input-group input-group-sm">
                        <span class="input-group-text bg-white border-secondary-subtle rounded-0"><i class="fa-solid fa-magnifying-glass text-muted"></i></span>
                        <input type="text" aria-label="Tìm thành viên" id="memberSearchInput" class="form-control rounded-0 border-secondary-subtle" placeholder="Tìm theo họ tên, tên đăng nhập, email...">
                    </div>
                </div>
                <div class="col-md-3">
                    <select aria-label="Lọc vai trò" id="memberRoleFilter" class="form-select form-select-sm rounded-0 border-secondary-subtle">
                        <option value="ALL">Tất cả vai trò</option>
                        <option value="MANAGER">Quản lý tổ chức</option>
                        <option value="CHECK_IN_STAFF">Soát vé tại cửa</option>
                    </select>
                </div>
                <div class="col-md-2">
                    <select aria-label="Lọc trạng thái" id="memberStatusFilter" class="form-select form-select-sm rounded-0 border-secondary-subtle">
                        <option value="ALL">Tất cả trạng thái</option>
                        <option value="ACTIVE">Đang hoạt động</option>
                        <option value="INACTIVE">Tạm khóa</option>
                    </select>
                </div>
                <div class="col-md-2 text-md-end">
                    <span class="badge bg-secondary-subtle text-secondary border px-2 py-1" id="memberCountBadge">0 thành viên</span>
                </div>
            </div>

            <!-- Members Table Panel -->
            <div class="panel-box bg-white mb-5">
                <div class="panel-header d-flex justify-content-between align-items-center p-3 border-bottom">
                    <div>
                        <span class="panel-title fw-bold h6 mb-0" id="memberTableTitle">Danh sách thành viên hiện tại</span>
                        <span class="small text-muted d-block" id="memberTableSubtitle">Đang kết nối dữ liệu...</span>
                    </div>
                </div>

                <div class="table-responsive">
                    <table class="table-custom">
                        <thead>
                            <tr>
                                <th style="width: 25%;">Họ tên & Tên đăng nhập</th>
                                <th style="width: 25%;">Email</th>
                                <th style="width: 15%;">Vai trò</th>
                                <th style="width: 12%;">Trạng thái</th>
                                <th style="width: 11%;">Ngày tham gia</th>
                                <th style="width: 12%;" class="text-end">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="memberTableBody">
                            <tr>
                                <td colspan="6" class="text-center py-4 text-muted">
                                    <div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>
                                    Đang nạp danh sách thành viên...
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </main>
    </div>

    <!-- Modal 1: Thêm thành viên -->
    <div class="modal fade" id="addMemberModal" tabindex="-1" aria-labelledby="addMemberModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content rounded-0">
                <form id="addMemberForm">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                    <div class="modal-header border-bottom">
                        <h5 class="modal-title h6 fw-bold" id="addMemberModalLabel">
                            <i class="fa-solid fa-user-plus me-2 text-primary"></i>Thêm thành viên vào tổ chức
                        </h5>
                        <button type="button" class="btn-close rounded-0" data-bs-dismiss="modal" aria-label="Đóng"></button>
                    </div>
                    <div class="modal-body">
                        <div id="addMemberAlert" class="alert d-none small mb-3"></div>

                        <div class="mb-3">
                            <label for="addUserName" class="form-label small fw-bold">Tên đăng nhập <span class="text-danger">*</span></label>
                            <input type="text" id="addUserName" name="userName" class="form-control rounded-0 border-secondary-subtle" required placeholder="vd: hoangphuc" autocomplete="off">
                            <div class="form-text small text-muted">Nhập tên đăng nhập tài khoản đã đăng ký trên hệ thống TicketsCenter.</div>
                        </div>

                        <div class="mb-3">
                            <label for="addMemberRole" class="form-label small fw-bold">Vai trò bổ nhiệm <span class="text-danger">*</span></label>
                            <select id="addMemberRole" name="role" class="form-select rounded-0 border-secondary-subtle" required>
                                <option value="CHECK_IN_STAFF" selected>Nhân viên soát vé tại cửa</option>
                                <option value="MANAGER">Quản lý tổ chức</option>
                            </select>
                            <div class="form-text small text-muted">Quản lý có toàn quyền sự kiện & nhân sự; Nhân viên chỉ có quyền quét vé tại cổng.</div>
                        </div>
                    </div>
                    <div class="modal-footer border-top bg-light">
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-0 px-3" data-bs-dismiss="modal">Hủy</button>
                        <button type="submit" class="btn btn-sm btn-dark-custom rounded-0 px-3" id="btnAddMemberSubmit">
                            <i class="fa-solid fa-check me-1"></i> Thêm thành viên
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- Modal 2: Đổi vai trò -->
    <div class="modal fade" id="changeRoleModal" tabindex="-1" aria-labelledby="changeRoleModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content rounded-0">
                <form id="changeRoleForm">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                    <div class="modal-header border-bottom">
                        <h5 class="modal-title h6 fw-bold" id="changeRoleModalLabel">
                            <i class="fa-solid fa-user-pen me-2 text-primary"></i>Thay đổi vai trò thành viên
                        </h5>
                        <button type="button" class="btn-close rounded-0" data-bs-dismiss="modal" aria-label="Đóng"></button>
                    </div>
                    <div class="modal-body">
                        <div id="changeRoleAlert" class="alert d-none small mb-3"></div>

                        <div class="p-3 bg-light border mb-3">
                            <div class="d-flex justify-content-between mb-1">
                                <span class="text-muted small">Họ tên:</span>
                                <strong id="changeRoleFullName" class="text-dark"></strong>
                            </div>
                            <div class="d-flex justify-content-between mb-1">
                                <span class="text-muted small">Tên đăng nhập:</span>
                                <span id="changeRoleUserName" class="font-monospace fw-semibold"></span>
                            </div>
                            <div class="d-flex justify-content-between">
                                <span class="text-muted small">Vai trò hiện tại:</span>
                                <span id="changeRoleCurrentBadge"></span>
                            </div>
                        </div>

                        <div class="mb-3">
                            <label for="changeRoleSelect" class="form-label small fw-bold">Vai trò mới <span class="text-danger">*</span></label>
                            <select id="changeRoleSelect" name="role" class="form-select rounded-0 border-secondary-subtle" required>
                                <option value="CHECK_IN_STAFF">Nhân viên soát vé tại cửa</option>
                                <option value="MANAGER">Quản lý tổ chức</option>
                            </select>
                        </div>

                        <div id="changeRoleSoleManagerNotice" class="alert alert-warning small d-none mb-0">
                            <i class="fa-solid fa-lock me-1"></i> Thành viên này là Quản lý duy nhất đang hoạt động. Không thể chuyển sang vai trò Soát vé trừ khi đã có Quản lý khác.
                        </div>
                    </div>
                    <div class="modal-footer border-top bg-light">
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-0 px-3" data-bs-dismiss="modal">Hủy</button>
                        <button type="submit" class="btn btn-sm btn-dark-custom rounded-0 px-3" id="btnChangeRoleSubmit">
                            <i class="fa-solid fa-floppy-disk me-1"></i> Lưu thay đổi
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- Modal 3: Xác nhận vô hiệu hóa -->
    <div class="modal fade" id="deactivateModal" tabindex="-1" aria-labelledby="deactivateModalLabel" aria-hidden="true">
        <div class="modal-dialog modal-dialog-centered">
            <div class="modal-content rounded-0">
                <form id="deactivateForm">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                    <div class="modal-header border-bottom">
                        <h5 class="modal-title h6 fw-bold text-danger" id="deactivateModalLabel">
                            <i class="fa-solid fa-triangle-exclamation me-2"></i>Xác nhận vô hiệu hóa thành viên
                        </h5>
                        <button type="button" class="btn-close rounded-0" data-bs-dismiss="modal" aria-label="Đóng"></button>
                    </div>
                    <div class="modal-body">
                        <div id="deactivateAlert" class="alert d-none small mb-3"></div>

                        <p class="text-secondary small mb-2">Bạn có chắc chắn muốn vô hiệu hóa quyền của thành viên sau?</p>
                        <div class="p-3 bg-light border mb-3">
                            <div class="fw-bold text-dark fs-6" id="deactivateTargetName"></div>
                            <div class="small text-muted font-monospace" id="deactivateTargetDetail"></div>
                        </div>

                        <div class="alert alert-danger small mb-0">
                            <i class="fa-solid fa-circle-exclamation me-1"></i> Thành viên sẽ bị thu hồi quyền truy cập trong tổ chức ngay lập tức. Lịch sử phân quyền và tài khoản của người dùng vẫn được bảo lưu an toàn.
                        </div>
                    </div>
                    <div class="modal-footer border-top bg-light">
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-0 px-3" data-bs-dismiss="modal">Hủy bỏ</button>
                        <button type="submit" class="btn btn-sm btn-danger rounded-0 px-3" id="btnDeactivateSubmit">
                            <i class="fa-solid fa-user-slash me-1"></i> Xác nhận vô hiệu hóa
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>



    <script>
    (function() {
        'use strict';

        const contextPath = '${pageContext.request.contextPath}';
        let currentMembers = [];
        let activeManagerCount = 0;
        let selectedMemberForEdit = null;
        let selectedMemberForDeactivate = null;

        // CSRF Token resolution
        async function getCsrfToken() {
            let token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content');
            if (token && token.trim()) return token.trim();

            const formToken = document.querySelector('input[name="_csrf"]')?.value;
            if (formToken && formToken.trim()) return formToken.trim();

            try {
                const resp = await fetch(contextPath + '/api/auth/csrf', { credentials: 'same-origin' });
                if (resp.ok) {
                    const data = await resp.json();
                    if (data?.data?.token) {
                        return data.data.token;
                    }
                }
            } catch (e) {
                console.warn('Could not fetch dynamic CSRF token:', e);
            }
            return '';
        }

        // Generic API caller
        async function apiCall(url, method = 'GET', bodyData = null) {
            const headers = {
                'Accept': 'application/json'
            };

            const options = {
                method: method,
                headers: headers,
                credentials: 'same-origin',
                cache: 'no-store'
            };

            if (method !== 'GET') {
                const token = await getCsrfToken();
                if (token) {
                    headers['X-CSRF-Token'] = token;
                }
                headers['Content-Type'] = 'application/json';
                const payload = bodyData ? { ...bodyData } : {};
                if (token) {
                    payload._csrf = token;
                }
                options.body = JSON.stringify(payload);
            }

            const res = await fetch(url, options);
            let json = null;
            try {
                json = await res.json();
            } catch (err) {
                // If not JSON
            }

            if (!res.ok) {
                const msg = json?.error?.message || json?.message || 'Yêu cầu không thành công (mã ' + res.status + ')';
                const error = new Error(msg);
                error.status = res.status;
                error.data = json;
                throw error;
            }

            return json;
        }

        // Alert helper
        function showAlert(msg, type = 'success', containerId = 'membersAlertPlaceholder') {
            const container = document.getElementById(containerId);
            if (!container) return;
            const icon = type === 'success' ? 'fa-circle-check' : 'fa-triangle-exclamation';
            container.innerHTML = `
                <div class="alert alert-${type} alert-dismissible fade show rounded-0 " role="alert">
                    <i class="fa-solid ${icon} me-2"></i> ${escapeHtml(msg)}
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Đóng"></button>
                </div>
            `;
        }

        function clearAlert(containerId = 'membersAlertPlaceholder') {
            const container = document.getElementById(containerId);
            if (container) container.innerHTML = '';
        }

        function escapeHtml(str) {
            if (str == null) return '';
            return String(str)
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#039;');
        }

        function formatDate(instantStr) {
            if (!instantStr) return '—';
            const d = new Date(instantStr);
            return isNaN(d.getTime()) ? instantStr : d.toLocaleString('vi-VN');
        }

        function getActiveOrgId() {
            return window.tcCurrentOrgId || window.TcOrgContext?.getCurrentOrgId() || localStorage.getItem('tc_current_organization_id');
        }

        function showBsModal(modalId) {
            const el = document.getElementById(modalId);
            if (el) bootstrap.Modal.getOrCreateInstance(el).show();
        }

        function hideBsModal(modalId) {
            const el = document.getElementById(modalId);
            if (el) bootstrap.Modal.getOrCreateInstance(el).hide();
        }

        // Load member list for active organization
        async function loadMembers() {
            const orgId = getActiveOrgId();
            const tbody = document.getElementById('memberTableBody');
            const subtitle = document.getElementById('memberTableSubtitle');

            if (!orgId) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="text-center py-4 text-muted">
                            <i class="fa-solid fa-arrow-up me-2 text-warning"></i>
                            Vui lòng chọn hoặc nạp tổ chức từ thanh điều khiển phía trên.
                        </td>
                    </tr>
                `;
                subtitle.textContent = 'Chưa chọn tổ chức';
                document.getElementById('memberCountBadge').textContent = '0 thành viên';
                return;
            }

            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center py-4 text-muted">
                        <div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>
                        Đang nạp danh sách thành viên...
                    </td>
                </tr>
            `;
            subtitle.textContent = 'Đang tải dữ liệu...';

            try {
                const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members', 'GET');
                const list = Array.isArray(res.data) ? res.data : (res.data?.members || res.data?.items || (Array.isArray(res) ? res : []));
                currentMembers = list || [];

                // Calculate active managers count
                activeManagerCount = currentMembers.filter(m => Boolean(m.isActive) && m.role === 'MANAGER').length;

                renderMembersTable();
                subtitle.textContent = 'Mã tổ chức: ' + orgId;
            } catch (err) {
                console.error('Error loading members:', err);
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="text-center py-4 text-danger">
                            <i class="fa-solid fa-circle-exclamation me-2"></i>
                            ${escapeHtml(err.message || 'Không thể tải danh sách thành viên.')}
                        </td>
                    </tr>
                `;
                subtitle.textContent = 'Lỗi nạp dữ liệu';
                document.getElementById('memberCountBadge').textContent = '0 thành viên';
            }
        }

        // Render table with filters
        function renderMembersTable() {
            const tbody = document.getElementById('memberTableBody');
            const searchKeyword = (document.getElementById('memberSearchInput').value || '').trim().toLowerCase();
            const roleFilter = document.getElementById('memberRoleFilter').value;
            const statusFilter = document.getElementById('memberStatusFilter').value;

            const filtered = currentMembers.filter(m => {
                const fullName = (m.fullName || '').toLowerCase();
                const userName = (m.userName || '').toLowerCase();
                const email = (m.email || '').toLowerCase();

                if (searchKeyword && !fullName.includes(searchKeyword) && !userName.includes(searchKeyword) && !email.includes(searchKeyword)) {
                    return false;
                }

                if (roleFilter !== 'ALL' && m.role !== roleFilter) {
                    return false;
                }

                const isAct = Boolean(m.isActive);

                if (statusFilter === 'ACTIVE' && !isAct) return false;
                if (statusFilter === 'INACTIVE' && isAct) return false;

                return true;
            });

            document.getElementById('memberCountBadge').textContent = filtered.length + ' thành viên';

            if (filtered.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="text-center py-4 text-muted">
                            <i class="fa-solid fa-user-slash me-2"></i> Không có thành viên nào phù hợp bộ lọc.
                        </td>
                    </tr>
                `;
                return;
            }

            tbody.innerHTML = '';
            filtered.forEach(m => {
                const userId = m.userId || m.id;
                const userName = m.userName || '';
                const fullName = m.fullName || userName || '—';
                const email = m.email || '—';
                const role = m.role || '';
                const isActive = Boolean(m.isActive);
                const joinedAt = m.joinedAt;

                const tr = document.createElement('tr');

                // Role badge
                let roleBadgeHtml = '';
                if (role === 'MANAGER') {
                    roleBadgeHtml = '<span class="badge badge-manager px-2 py-1"><i class="fa-solid fa-user-tie me-1"></i>Quản lý tổ chức</span>';
                } else if (role === 'CHECK_IN_STAFF') {
                    roleBadgeHtml = '<span class="badge badge-staff px-2 py-1"><i class="fa-solid fa-qrcode me-1"></i>Soát vé tại cửa</span>';
                } else {
                    roleBadgeHtml = `<span class="badge bg-secondary px-2 py-1">${escapeHtml(role)}</span>`;
                }

                // Status badge
                let statusBadgeHtml = '';
                if (isActive) {
                    statusBadgeHtml = '<span class="badge badge-active px-2 py-1"><i class="fa-solid fa-circle-check me-1"></i>Đang hoạt động</span>';
                } else {
                    statusBadgeHtml = '<span class="badge badge-inactive px-2 py-1"><i class="fa-solid fa-circle-xmark me-1"></i>Tạm khóa</span>';
                }

                // Invariant check: Single active manager protection
                const isSoleActiveManager = (role === 'MANAGER' && isActive && activeManagerCount <= 1);

                // Actions cell
                let actionsHtml = '';
                if (isActive) {
                    actionsHtml = `
                        <div class="btn-group btn-group-sm">
                            <button type="button" class="btn btn-outline-dark rounded-0 px-2 py-1 btn-change-role" data-user-id="${escapeHtml(userId)}">
                                <i class="fa-solid fa-user-gear me-1"></i>Đổi vai trò
                            </button>
                    `;

                    if (isSoleActiveManager) {
                        actionsHtml += `
                            <span class="d-inline-block" tabindex="0" data-bs-toggle="tooltip" data-bs-placement="top" title="Không thể vô hiệu hóa Quản lý duy nhất còn lại của tổ chức">
                                <button type="button" class="btn btn-outline-secondary rounded-0 px-2 py-1" disabled style="pointer-events: none;">
                                    <i class="fa-solid fa-lock me-1"></i>Vô hiệu hóa
                                </button>
                            </span>
                        `;
                    } else {
                        actionsHtml += `
                            <button type="button" class="btn btn-outline-danger rounded-0 px-2 py-1 btn-deactivate" data-user-id="${escapeHtml(userId)}">
                                <i class="fa-solid fa-user-slash me-1"></i>Vô hiệu hóa
                            </button>
                        `;
                    }

                    actionsHtml += `</div>`;
                } else {
                    actionsHtml = `
                        <button type="button" class="btn btn-sm btn-outline-success rounded-0 px-2 py-1 btn-activate" data-user-id="${escapeHtml(userId)}">
                            <i class="fa-solid fa-user-check me-1"></i>Kích hoạt lại
                        </button>
                    `;
                }

                // If sole manager, display lock tag next to role or name
                const soleLockBadge = isSoleActiveManager
                    ? '<span class="badge badge-locked ms-1" title="Quản lý duy nhất còn lại"><i class="fa-solid fa-lock text-warning"></i> Quản lý cuối</span>'
                    : '';

                tr.innerHTML = `
                    <td>
                        <strong class="d-block text-dark">${escapeHtml(fullName)} ${soleLockBadge}</strong>
                        <span class="small text-muted font-monospace"><i class="fa-regular fa-user me-1"></i>${escapeHtml(userName)}</span>
                    </td>
                    <td>
                        <span class="text-body">${escapeHtml(email)}</span>
                    </td>
                    <td>${roleBadgeHtml}</td>
                    <td>${statusBadgeHtml}</td>
                    <td><span class="small text-muted">${formatDate(joinedAt)}</span></td>
                    <td class="text-end">${actionsHtml}</td>
                `;

                tbody.appendChild(tr);
            });

            // Initialize tooltips if Bootstrap exists
            if (window.bootstrap && window.bootstrap.Tooltip) {
                const tooltipTriggerList = [].slice.call(tbody.querySelectorAll('[data-bs-toggle="tooltip"]'));
                tooltipTriggerList.forEach(function (tooltipTriggerEl) {
                    new bootstrap.Tooltip(tooltipTriggerEl);
                });
            }

            // Attach row action listeners
            attachRowActionListeners();
        }

        function attachRowActionListeners() {
            // Change Role buttons
            document.querySelectorAll('.btn-change-role').forEach(btn => {
                btn.addEventListener('click', function() {
                    const userId = this.dataset.userId;
                    const member = currentMembers.find(m => (m.userId || m.id) === userId);
                    if (!member) return;
                    openChangeRoleModal(member);
                });
            });

            // Deactivate buttons
            document.querySelectorAll('.btn-deactivate').forEach(btn => {
                btn.addEventListener('click', function() {
                    const userId = this.dataset.userId;
                    const member = currentMembers.find(m => (m.userId || m.id) === userId);
                    if (!member) return;
                    openDeactivateModal(member);
                });
            });

            // Activate buttons
            document.querySelectorAll('.btn-activate').forEach(btn => {
                btn.addEventListener('click', function() {
                    const userId = this.dataset.userId;
                    const member = currentMembers.find(m => (m.userId || m.id) === userId);
                    if (!member) return;
                    handleActivateMember(member);
                });
            });
        }

        // Modal Action: Add Member
        document.getElementById('btnOpenAddMember').addEventListener('click', function() {
            const orgId = getActiveOrgId();
            if (!orgId) {
                showAlert('Vui lòng chọn tổ chức trước khi thêm thành viên.', 'warning');
                return;
            }
            document.getElementById('addMemberForm').reset();
            const alertBox = document.getElementById('addMemberAlert');
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';
            showBsModal('addMemberModal');
        });

        document.getElementById('addMemberForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            const orgId = getActiveOrgId();
            if (!orgId) return;

            const userName = document.getElementById('addUserName').value.trim();
            const role = document.getElementById('addMemberRole').value;
            const submitBtn = document.getElementById('btnAddMemberSubmit');
            const alertBox = document.getElementById('addMemberAlert');

            if (!userName) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = 'Vui lòng nhập tên đăng nhập (userName).';
                return;
            }

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang thêm...';
            alertBox.className = 'alert d-none small mb-3';

            try {
                const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members', 'POST', {
                    userName: userName,
                    role: role
                });

                hideBsModal('addMemberModal');
                showAlert(res?.message || `Đã thêm thành viên "${userName}" vào tổ chức thành công.`, 'success');
                await loadMembers();
            } catch (err) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể thêm thành viên.';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-check me-1"></i> Thêm thành viên';
            }
        });

        // Modal Action: Change Role
        function openChangeRoleModal(member) {
            selectedMemberForEdit = member;
            document.getElementById('changeRoleFullName').textContent = member.fullName || member.userName;
            document.getElementById('changeRoleUserName').textContent = member.userName;

            const badgeSpan = document.getElementById('changeRoleCurrentBadge');
            if (member.role === 'MANAGER') {
                badgeSpan.innerHTML = '<span class="badge badge-manager">Quản lý tổ chức</span>';
            } else {
                badgeSpan.innerHTML = '<span class="badge badge-staff">Soát vé tại cửa</span>';
            }

            const select = document.getElementById('changeRoleSelect');
            select.value = member.role;

            const isSoleManager = (member.role === 'MANAGER' && activeManagerCount <= 1);
            const notice = document.getElementById('changeRoleSoleManagerNotice');
            const staffOption = select.querySelector('option[value="CHECK_IN_STAFF"]');

            if (isSoleManager) {
                notice.classList.remove('d-none');
                staffOption.disabled = true;
            } else {
                notice.classList.add('d-none');
                staffOption.disabled = false;
            }

            const alertBox = document.getElementById('changeRoleAlert');
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';

            showBsModal('changeRoleModal');
        }

        document.getElementById('changeRoleForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            if (!selectedMemberForEdit) return;

            const orgId = getActiveOrgId();
            const userId = selectedMemberForEdit.userId || selectedMemberForEdit.id;
            const newRole = document.getElementById('changeRoleSelect').value;
            const submitBtn = document.getElementById('btnChangeRoleSubmit');
            const alertBox = document.getElementById('changeRoleAlert');

            if (selectedMemberForEdit.role === 'MANAGER' && newRole === 'CHECK_IN_STAFF' && activeManagerCount <= 1) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = 'Không thể hạ quyền Quản lý duy nhất còn lại của tổ chức.';
                return;
            }

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang lưu...';
            alertBox.className = 'alert d-none small mb-3';

            try {
                const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members/' + encodeURIComponent(userId) + '/role', 'POST', {
                    role: newRole
                });

                hideBsModal('changeRoleModal');
                showAlert(res?.message || 'Đã cập nhật vai trò thành viên thành công.', 'success');
                await loadMembers();
            } catch (err) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể đổi vai trò thành viên.';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-floppy-disk me-1"></i> Lưu thay đổi';
            }
        });

        // Modal Action: Deactivate
        function openDeactivateModal(member) {
            selectedMemberForDeactivate = member;
            document.getElementById('deactivateTargetName').textContent = member.fullName || member.userName;
            document.getElementById('deactivateTargetDetail').textContent = `${member.userName} • ${member.email || ''} • [${member.role}]`;

            const alertBox = document.getElementById('deactivateAlert');
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';

            showBsModal('deactivateModal');
        }

        document.getElementById('deactivateForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            if (!selectedMemberForDeactivate) return;

            const orgId = getActiveOrgId();
            const userId = selectedMemberForDeactivate.userId || selectedMemberForDeactivate.id;
            const submitBtn = document.getElementById('btnDeactivateSubmit');
            const alertBox = document.getElementById('deactivateAlert');

            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang xử lý...';
            alertBox.className = 'alert d-none small mb-3';

            try {
                const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members/' + encodeURIComponent(userId) + '/deactivate', 'POST');

                hideBsModal('deactivateModal');
                showAlert(res?.message || `Đã vô hiệu hóa thành viên "${selectedMemberForDeactivate.fullName || selectedMemberForDeactivate.userName}" thành công.`, 'success');
                await loadMembers();
            } catch (err) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể vô hiệu hóa thành viên.';
            } finally {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-user-slash me-1"></i> Xác nhận vô hiệu hóa';
            }
        });

        // Activate Action
        async function handleActivateMember(member) {
            const orgId = getActiveOrgId();
            const userId = member.userId || member.id;
            const memberName = member.fullName || member.userName;

            clearAlert();
            try {
                const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members/' + encodeURIComponent(userId) + '/activate', 'POST');
                showAlert(res?.message || `Đã kích hoạt lại thành viên "${memberName}" thành công.`, 'success');
                await loadMembers();
            } catch (err) {
                showAlert(err.message || 'Không thể kích hoạt lại thành viên.', 'danger');
            }
        }

        // Toolbar listeners
        document.getElementById('memberSearchInput').addEventListener('input', renderMembersTable);
        document.getElementById('memberRoleFilter').addEventListener('change', renderMembersTable);
        document.getElementById('memberStatusFilter').addEventListener('change', renderMembersTable);
        document.getElementById('btnReloadMembers').addEventListener('click', loadMembers);

        // Listen for organization context change event from context-switch.jsp
        window.addEventListener('tc:org-changed', function(e) {
            if (e.detail && e.detail.organizationId) {
                loadMembers();
            }
        });

        // Expose helper on window
        window.TcMembersList = {
            load: loadMembers
        };

        // Initial load if org already known
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', function() {
                if (getActiveOrgId()) {
                    loadMembers();
                }
            });
        } else {
            if (getActiveOrgId()) {
                loadMembers();
            }
        }
    })();
    </script>
</body>
</html>
