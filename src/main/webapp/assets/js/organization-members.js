    (function() {
        'use strict';

        const contextPath = window.TC_CONTEXT_PATH
            || document.querySelector('meta[name="context-path"]')?.getAttribute('content')
            || (window.location.pathname.startsWith('/ticketscenter') ? '/ticketscenter' : '')
            || '';
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
                <div class="alert alert-${type} alert-dismissible fade show rounded-0 shadow-sm" role="alert">
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
