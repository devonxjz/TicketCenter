(function() {
    'use strict';

    /**
     * Quản lý thành viên tổ chức (Organization Members)
     * Tư duy tuần tự: 1. Hằng số & DOM -> 2. Trạng thái -> 3. Render -> 4. Hành động -> 5. Sự kiện
     */

    // 1. HẰNG SỐ & DOM HELPERS
    const contextPath = window.TC_CONTEXT_PATH ||
        document.querySelector('meta[name="context-path"]')?.getAttribute('content') ||
        (window.location?.pathname?.startsWith('/ticketscenter') ? '/ticketscenter' : '');

    const modalButtons = {
        addMemberModal: ['btnAddMemberSubmit', '<i class="fa-solid fa-check me-1"></i> Thêm thành viên'],
        changeRoleModal: ['btnChangeRoleSubmit', '<i class="fa-solid fa-floppy-disk me-1"></i> Lưu thay đổi'],
        deactivateModal: ['btnDeactivateSubmit', '<i class="fa-solid fa-user-slash me-1"></i> Xác nhận vô hiệu hóa']
    };

    function getActiveOrgId() {
        if (window.tcCurrentOrgId !== undefined) return window.tcCurrentOrgId;
        return window.TcOrgContext?.getCurrentOrgId() || localStorage?.getItem('tc_current_organization_id');
    }

    function showBsModal(modalId) {
        const el = document.getElementById(modalId);
        if (el && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            bootstrap.Modal.getOrCreateInstance(el).show();
        }
    }

    function hideBsModal(modalId) {
        const el = document.getElementById(modalId);
        if (el && typeof bootstrap !== 'undefined' && bootstrap.Modal) {
            bootstrap.Modal.getOrCreateInstance(el).hide();
        }
    }

    // 2. TRẠNG THÁI (STATE)
    let currentMembers = [];
    let activeManagerCount = 0;
    let memberRequest = 0;
    let organizationScope = 0;
    let observedOrgId = getActiveOrgId();
    const modalContexts = {};

    // 3. RENDER FUNCTIONS

    function showAlert(msg, type = 'success', containerId = 'membersAlertPlaceholder') {
        const container = document.getElementById(containerId);
        if (!container) return;
        const icon = type === 'success' ? 'fa-circle-check' : 'fa-triangle-exclamation';
        container.innerHTML = `
            <div class="alert alert-${type} alert-dismissible fade show rounded-0" role="alert">
                <i class="fa-solid ${icon} me-2"></i> ${TC.escapeHtml(msg)}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Đóng"></button>
            </div>
        `;
    }

    function clearAlert(containerId = 'membersAlertPlaceholder') {
        const container = document.getElementById(containerId);
        if (container) container.innerHTML = '';
    }

    function renderMembersTable() {
        const tbody = document.getElementById('memberTableBody');
        if (!tbody) return;

        const searchKeyword = (document.getElementById('memberSearchInput')?.value || '').trim().toLowerCase();
        const roleFilter = document.getElementById('memberRoleFilter')?.value || 'ALL';
        const statusFilter = document.getElementById('memberStatusFilter')?.value || 'ALL';

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

        const badgeEl = document.getElementById('memberCountBadge');
        if (badgeEl) badgeEl.textContent = filtered.length + ' thành viên';

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
                roleBadgeHtml = `<span class="badge bg-secondary px-2 py-1">${TC.escapeHtml(role)}</span>`;
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
                        <button type="button" class="btn btn-outline-dark rounded-0 px-2 py-1 btn-change-role" data-user-id="${TC.escapeHtml(userId)}">
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
                        <button type="button" class="btn btn-outline-danger rounded-0 px-2 py-1 btn-deactivate" data-user-id="${TC.escapeHtml(userId)}">
                            <i class="fa-solid fa-user-slash me-1"></i>Vô hiệu hóa
                        </button>
                    `;
                }

                actionsHtml += `</div>`;
            } else {
                actionsHtml = `
                    <button type="button" class="btn btn-sm btn-outline-success rounded-0 px-2 py-1 btn-activate" data-user-id="${TC.escapeHtml(userId)}">
                        <i class="fa-solid fa-user-check me-1"></i>Kích hoạt lại
                    </button>
                `;
            }

            const soleLockBadge = isSoleActiveManager ?
                '<span class="badge badge-locked ms-1" title="Quản lý duy nhất còn lại"><i class="fa-solid fa-lock text-warning"></i> Quản lý cuối</span>' :
                '';

            tr.innerHTML = `
                <td>
                    <strong class="d-block text-dark">${TC.escapeHtml(fullName)} ${soleLockBadge}</strong>
                    <span class="small text-muted font-monospace"><i class="fa-regular fa-user me-1"></i>${TC.escapeHtml(userName)}</span>
                </td>
                <td><span class="text-body">${TC.escapeHtml(email)}</span></td>
                <td>${roleBadgeHtml}</td>
                <td>${statusBadgeHtml}</td>
                <td><span class="small text-muted">${TC.date(joinedAt)}</span></td>
                <td class="text-end">${actionsHtml}</td>
            `;

            tbody.appendChild(tr);
        });

        // Initialize tooltips if Bootstrap exists
        if (window.bootstrap?.Tooltip) {
            const tooltipTriggerList = [].slice.call(tbody.querySelectorAll('[data-bs-toggle="tooltip"]'));
            tooltipTriggerList.forEach(el => new bootstrap.Tooltip(el));
        }

        attachRowActionListeners();
    }

    // 4. HÀNH ĐỘNG (ACTIONS)

    function isCurrentScope(context) {
        return !!(context && context.scope === organizationScope && context.orgId === getActiveOrgId());
    }

    function isCurrentModal(id, context) {
        return modalContexts[id] === context && isCurrentScope(context);
    }

    function beginModal(id, member) {
        const context = {
            orgId: getActiveOrgId(),
            scope: organizationScope,
            member,
            busy: false
        };
        modalContexts[id] = context;
        const [buttonId, label] = modalButtons[id];
        const btn = document.getElementById(buttonId);
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = label;
        }
        return context;
    }

    function invalidateOrganization() {
        organizationScope++;
        for (const id of Object.keys(modalButtons)) {
            delete modalContexts[id];
            hideBsModal(id);
        }
        clearAlert();
    }

    async function getCsrfToken() {
        let token = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content');
        if (token && token.trim()) return token.trim();

        const formToken = document.querySelector('input[name="_csrf"]')?.value;
        if (formToken && formToken.trim()) return formToken.trim();

        try {
            const resp = await fetch(contextPath + '/api/auth/csrf', {
                credentials: 'same-origin'
            });
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

    async function apiCall(url, method = 'GET', bodyData = null, isValid = () => true) {
        const headers = { 'Accept': 'application/json' };
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

        if (!isValid()) throw new Error('Thao tác đã hết hiệu lực.');
        const res = await fetch(url, options);
        let json = null;
        try {
            json = await res.json();
        } catch {}

        if (!res.ok) {
            const msg = json?.error?.message || json?.message || `Yêu cầu không thành công (mã ${res.status})`;
            const error = new Error(msg);
            error.status = res.status;
            error.data = json;
            throw error;
        }

        return json;
    }

    async function loadMembers() {
        const orgId = getActiveOrgId();
        if (orgId !== observedOrgId) {
            observedOrgId = orgId;
            invalidateOrganization();
        }
        const request = ++memberRequest;
        currentMembers = [];
        activeManagerCount = 0;
        const badgeEl = document.getElementById('memberCountBadge');
        if (badgeEl) badgeEl.textContent = '0 thành viên';
        const tbody = document.getElementById('memberTableBody');
        const subtitle = document.getElementById('memberTableSubtitle');

        if (!orgId) {
            if (tbody) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="text-center py-4 text-muted">
                            <i class="fa-solid fa-arrow-up me-2 text-warning"></i>
                            Vui lòng chọn hoặc nạp tổ chức từ thanh điều khiển phía trên.
                        </td>
                    </tr>
                `;
            }
            if (subtitle) subtitle.textContent = 'Chưa chọn tổ chức';
            return;
        }

        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="text-center py-4 text-muted">
                        <div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>
                        Đang nạp danh sách thành viên...
                    </td>
                </tr>
            `;
        }
        if (subtitle) subtitle.textContent = 'Đang tải dữ liệu...';

        try {
            const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members', 'GET');
            if (request !== memberRequest || orgId !== getActiveOrgId()) return;
            const list = Array.isArray(res.data) ? res.data : (res.data?.members || res.data?.items || (Array.isArray(res) ? res : []));
            currentMembers = list || [];
            activeManagerCount = currentMembers.filter(m => Boolean(m.isActive) && m.role === 'MANAGER').length;

            renderMembersTable();
            if (subtitle) {
                subtitle.textContent = document.querySelector('#tcOrgSelect option:checked')?.dataset.orgName || 'Thành viên tổ chức';
            }
        } catch (err) {
            if (request !== memberRequest || orgId !== getActiveOrgId()) return;
            console.error('Error loading members:', err);
            if (tbody) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="6" class="text-center py-4 text-danger">
                            <i class="fa-solid fa-circle-exclamation me-2"></i>
                            ${TC.escapeHtml(err.message || 'Không thể tải danh sách thành viên.')}
                        </td>
                    </tr>
                `;
            }
            if (subtitle) subtitle.textContent = 'Lỗi nạp dữ liệu';
            if (badgeEl) badgeEl.textContent = '0 thành viên';
        }
    }

    function openChangeRoleModal(member) {
        beginModal('changeRoleModal', member);
        const nameEl = document.getElementById('changeRoleFullName');
        if (nameEl) nameEl.textContent = member.fullName || member.userName;
        const userEl = document.getElementById('changeRoleUserName');
        if (userEl) userEl.textContent = member.userName;

        const badgeSpan = document.getElementById('changeRoleCurrentBadge');
        if (badgeSpan) {
            if (member.role === 'MANAGER') {
                badgeSpan.innerHTML = '<span class="badge badge-manager">Quản lý tổ chức</span>';
            } else {
                badgeSpan.innerHTML = '<span class="badge badge-staff">Soát vé tại cửa</span>';
            }
        }

        const select = document.getElementById('changeRoleSelect');
        if (select) select.value = member.role;

        const isSoleManager = (member.role === 'MANAGER' && activeManagerCount <= 1);
        const notice = document.getElementById('changeRoleSoleManagerNotice');
        const staffOption = select?.querySelector('option[value="CHECK_IN_STAFF"]');

        if (notice) {
            if (isSoleManager) {
                notice.classList.remove('d-none');
                if (staffOption) staffOption.disabled = true;
            } else {
                notice.classList.add('d-none');
                if (staffOption) staffOption.disabled = false;
            }
        }

        const alertBox = document.getElementById('changeRoleAlert');
        if (alertBox) {
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';
        }

        showBsModal('changeRoleModal');
    }

    function openDeactivateModal(member) {
        beginModal('deactivateModal', member);
        const nameEl = document.getElementById('deactivateTargetName');
        if (nameEl) nameEl.textContent = member.fullName || member.userName;
        const detailEl = document.getElementById('deactivateTargetDetail');
        if (detailEl) {
            detailEl.textContent = `${member.userName} • ${member.email || ''} • ${member.role === 'MANAGER' ? 'Quản lý tổ chức' : 'Soát vé tại cửa'}`;
        }

        const alertBox = document.getElementById('deactivateAlert');
        if (alertBox) {
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';
        }

        showBsModal('deactivateModal');
    }

    async function handleActivateMember(member) {
        const orgId = getActiveOrgId();
        const context = { orgId, scope: organizationScope };
        const userId = member.userId || member.id;
        const memberName = member.fullName || member.userName;

        clearAlert();
        try {
            const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) +
                '/members/' + encodeURIComponent(userId) + '/activate', 'POST', null, () => isCurrentScope(context));
            if (!isCurrentScope(context)) return;
            showAlert(res?.message || `Đã kích hoạt lại thành viên "${memberName}" thành công.`, 'success');
            await loadMembers();
        } catch (err) {
            if (!isCurrentScope(context)) return;
            showAlert(err.message || 'Không thể kích hoạt lại thành viên.', 'danger');
        }
    }

    async function submitAddMember(e) {
        if (e?.preventDefault) e.preventDefault();
        const context = modalContexts.addMemberModal;
        if (!isCurrentModal('addMemberModal', context) || context.busy) return;
        const orgId = context.orgId;

        const userName = document.getElementById('addUserName')?.value.trim() || '';
        const role = document.getElementById('addMemberRole')?.value || 'CHECK_IN_STAFF';
        const submitBtn = document.getElementById('btnAddMemberSubmit');
        const alertBox = document.getElementById('addMemberAlert');

        if (!userName) {
            if (alertBox) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = 'Vui lòng nhập tên đăng nhập.';
            }
            return;
        }

        context.busy = true;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang thêm...';
        }
        if (alertBox) alertBox.className = 'alert d-none small mb-3';

        try {
            const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) + '/members', 'POST', {
                userName: userName,
                role: role
            }, () => isCurrentModal('addMemberModal', context));

            if (!isCurrentModal('addMemberModal', context)) return;
            hideBsModal('addMemberModal');
            showAlert(res?.message || `Đã thêm thành viên "${userName}" vào tổ chức thành công.`, 'success');
            await loadMembers();
        } catch (err) {
            if (!isCurrentModal('addMemberModal', context)) return;
            if (alertBox) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể thêm thành viên.';
            }
        } finally {
            if (modalContexts['addMemberModal'] !== context) return;
            context.busy = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-check me-1"></i> Thêm thành viên';
            }
        }
    }

    async function submitChangeRole(e) {
        if (e?.preventDefault) e.preventDefault();
        const context = modalContexts.changeRoleModal;
        if (!isCurrentModal('changeRoleModal', context) || context.busy) return;
        const member = context.member;
        const orgId = context.orgId;
        const userId = member.userId || member.id;
        const newRole = document.getElementById('changeRoleSelect')?.value;
        const submitBtn = document.getElementById('btnChangeRoleSubmit');
        const alertBox = document.getElementById('changeRoleAlert');

        if (member.role === 'MANAGER' && newRole === 'CHECK_IN_STAFF' && activeManagerCount <= 1) {
            if (alertBox) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = 'Không thể hạ quyền Quản lý duy nhất còn lại của tổ chức.';
            }
            return;
        }

        context.busy = true;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang lưu...';
        }
        if (alertBox) alertBox.className = 'alert d-none small mb-3';

        try {
            const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) +
                '/members/' + encodeURIComponent(userId) + '/role', 'POST', {
                    role: newRole
                }, () => isCurrentModal('changeRoleModal', context));

            if (!isCurrentModal('changeRoleModal', context)) return;
            hideBsModal('changeRoleModal');
            showAlert(res?.message || 'Đã cập nhật vai trò thành viên thành công.', 'success');
            await loadMembers();
        } catch (err) {
            if (!isCurrentModal('changeRoleModal', context)) return;
            if (alertBox) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể đổi vai trò thành viên.';
            }
        } finally {
            if (modalContexts['changeRoleModal'] !== context) return;
            context.busy = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-floppy-disk me-1"></i> Lưu thay đổi';
            }
        }
    }

    async function submitDeactivate(e) {
        if (e?.preventDefault) e.preventDefault();
        const context = modalContexts.deactivateModal;
        if (!isCurrentModal('deactivateModal', context) || context.busy) return;
        const member = context.member;
        const orgId = context.orgId;
        const userId = member.userId || member.id;
        if (member.role === 'MANAGER' && member.isActive && activeManagerCount <= 1) return;
        const submitBtn = document.getElementById('btnDeactivateSubmit');
        const alertBox = document.getElementById('deactivateAlert');

        context.busy = true;
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status"></span> Đang xử lý...';
        }
        if (alertBox) alertBox.className = 'alert d-none small mb-3';

        try {
            const res = await apiCall(contextPath + '/api/organizations/' + encodeURIComponent(orgId) +
                '/members/' + encodeURIComponent(userId) + '/deactivate', 'POST', null, () =>
                isCurrentModal('deactivateModal', context));

            if (!isCurrentModal('deactivateModal', context)) return;
            hideBsModal('deactivateModal');
            showAlert(res?.message || `Đã vô hiệu hóa thành viên "${member.fullName || member.userName}" thành công.`, 'success');
            await loadMembers();
        } catch (err) {
            if (!isCurrentModal('deactivateModal', context)) return;
            if (alertBox) {
                alertBox.className = 'alert alert-danger small mb-3';
                alertBox.textContent = err.message || 'Không thể vô hiệu hóa thành viên.';
            }
        } finally {
            if (modalContexts['deactivateModal'] !== context) return;
            context.busy = false;
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.innerHTML = '<i class="fa-solid fa-user-slash me-1"></i> Xác nhận vô hiệu hóa';
            }
        }
    }

    // 5. GẮN SỰ KIỆN & KHỞI TẠO (EVENTS & INITIALIZATION)

    function attachRowActionListeners() {
        document.querySelectorAll('.btn-change-role').forEach(btn => {
            btn.addEventListener('click', function() {
                const userId = this.dataset.userId;
                const member = currentMembers.find(m => (m.userId || m.id) === userId);
                if (member) openChangeRoleModal(member);
            });
        });

        document.querySelectorAll('.btn-deactivate').forEach(btn => {
            btn.addEventListener('click', function() {
                const userId = this.dataset.userId;
                const member = currentMembers.find(m => (m.userId || m.id) === userId);
                if (member) openDeactivateModal(member);
            });
        });

        document.querySelectorAll('.btn-activate').forEach(btn => {
            btn.addEventListener('click', function() {
                const userId = this.dataset.userId;
                const member = currentMembers.find(m => (m.userId || m.id) === userId);
                if (member) handleActivateMember(member);
            });
        });
    }

    // Modal hide listeners
    for (const id of Object.keys(modalButtons)) {
        document.getElementById(id)?.addEventListener('hide.bs.modal', () => {
            delete modalContexts[id];
        });
    }

    // Toolbar filters & reload
    document.getElementById('memberSearchInput')?.addEventListener('input', renderMembersTable);
    document.getElementById('memberRoleFilter')?.addEventListener('change', renderMembersTable);
    document.getElementById('memberStatusFilter')?.addEventListener('change', renderMembersTable);
    document.getElementById('btnReloadMembers')?.addEventListener('click', loadMembers);

    // Open Add Member modal
    document.getElementById('btnOpenAddMember')?.addEventListener('click', function() {
        const orgId = getActiveOrgId();
        if (!orgId) {
            showAlert('Vui lòng chọn tổ chức trước khi thêm thành viên.', 'warning');
            return;
        }
        beginModal('addMemberModal');
        document.getElementById('addMemberForm')?.reset();
        const alertBox = document.getElementById('addMemberAlert');
        if (alertBox) {
            alertBox.className = 'alert d-none small mb-3';
            alertBox.textContent = '';
        }
        showBsModal('addMemberModal');
    });

    // Form submits
    document.getElementById('addMemberForm')?.addEventListener('submit', submitAddMember);
    document.getElementById('changeRoleForm')?.addEventListener('submit', submitChangeRole);
    document.getElementById('deactivateForm')?.addEventListener('submit', submitDeactivate);

    // Listen for organization context change event
    window.addEventListener('tc:org-changed', function(e) {
        if (e.detail && Object.prototype.hasOwnProperty.call(e.detail, 'organizationId')) {
            window.tcCurrentOrgId = e.detail.organizationId;
        }
        invalidateOrganization();
        observedOrgId = getActiveOrgId();
        loadMembers();
    });

    // Expose helper on window
    window.TcMembersList = {
        load: loadMembers
    };

    // Initial load
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
