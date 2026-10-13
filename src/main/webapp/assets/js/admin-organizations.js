const contextPath = document.querySelector('meta[name="context-path"]').getAttribute('content') || '';
        const csrfToken = document.querySelector('meta[name="csrf-token"]').getAttribute('content') || '';

        let currentStatus = 'PENDING_APPROVAL';
        let currentPage = 1;
        const pageSize = 6;
        let totalRecords = 0;

        const approveModal = new bootstrap.Modal(document.getElementById('approveModal'));
        const rejectModal = new bootstrap.Modal(document.getElementById('rejectModal'));

        // Initialize today's date for effectiveDate
        document.getElementById('approveEffectiveDate').value = new Date().toISOString().split('T')[0];

        // Setup tab event listeners
        document.querySelectorAll('#orgStatusTabs button').forEach(tabBtn => {
            tabBtn.addEventListener('click', (e) => {
                document.querySelectorAll('#orgStatusTabs button').forEach(b => b.classList.remove('active'));
                tabBtn.classList.add('active');
                currentStatus = tabBtn.getAttribute('data-status');
                currentPage = 1;
                loadRequests();
            });
        });

        function showAlert(type, message) {
            const placeholder = document.getElementById('alertPlaceholder');
            placeholder.innerHTML = `
                <div class="alert alert-${type} alert-dismissible fade show rounded-0 mb-4" role="alert">
                    <i class="fa-solid ${type === 'success' ? 'fa-circle-check' : 'fa-triangle-exclamation'} me-2"></i>
                    ${escapeHtml(message)}
                    <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                </div>
            `;
            setTimeout(() => {
                const alertEl = placeholder.querySelector('.alert');
                if (alertEl) {
                    const bsAlert = bootstrap.Alert.getInstance(alertEl);
                    if (bsAlert) bsAlert.close();
                    else alertEl.remove();
                }
            }, 6000);
        }

        function updateCharCount() {
            const val = document.getElementById('rejectReason').value;
            document.getElementById('charCounter').textContent = val.length + '/500 ký tự';
            const msgEl = document.getElementById('reasonValidationMsg');
            if (val.trim().length > 0 && val.trim().length < 10) {
                msgEl.textContent = 'Lý do phải có ít nhất 10 ký tự.';
            } else {
                msgEl.textContent = '';
            }
        }

        async function loadRequests() {
            const tbody = document.getElementById('orgTableBody');
            tbody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center py-4 text-muted">
                        <div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>
                        Đang tải danh sách hồ sơ...
                    </td>
                </tr>
            `;

            let url = `${contextPath}/api/admin/organization-requests?page=${currentPage}&pageSize=${pageSize}`;
            if (currentStatus && currentStatus !== 'ALL') {
                url += `&status=${currentStatus}`;
            }

            try {
                const response = await fetch(url, {
                    method: 'GET',
                    headers: { 'Accept': 'application/json' }
                });

                if (!response.ok) {
                    throw new Error('Không thể tải dữ liệu: ' + response.statusText);
                }

                const payload = await response.json();
                const data = payload.data;
                if (!data || !Array.isArray(data.items) || !Number.isSafeInteger(data.total)) throw new Error('Dữ liệu hồ sơ không hợp lệ.');
                const items = data.items;
                totalRecords = data.total || 0;

                updateHeaderAndBadges(data);
                renderTable(items);
                renderPagination(data);
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center py-4 text-danger">
                            <i class="fa-solid fa-circle-exclamation me-1"></i> Có lỗi xảy ra khi tải dữ liệu: ${escapeHtml(err.message)}
                        </td>
                    </tr>
                `;
            }
        }

        function updateHeaderAndBadges(data) {
            const titleMap = {
                'PENDING_APPROVAL': 'Danh sách hồ sơ chờ xét duyệt',
                'APPROVED': 'Danh sách hồ sơ đã phê duyệt',
                'REJECTED': 'Danh sách hồ sơ đã từ chối',
                'ALL': 'Tất cả hồ sơ đối tác'
            };
            document.getElementById('tableHeaderTitle').textContent = titleMap[currentStatus] || 'Danh sách hồ sơ';
            document.getElementById('tableHeaderSubtitle').textContent = `Tổng cộng ${totalRecords} hồ sơ được tìm thấy`;

            if (currentStatus === 'PENDING_APPROVAL') {
                document.getElementById('tabBadgePending').textContent = totalRecords;
            }
        }

        function escapeHtml(text) {
            return String(text ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
        }

        function formatDate(dateStr) {
            if (!dateStr) return '—';
            try {
                const d = new Date(dateStr);
                return d.toLocaleDateString('vi-VN') + ' ' + d.toLocaleTimeString('vi-VN', {hour: '2-digit', minute: '2-digit'});
            } catch (e) {
                return dateStr;
            }
        }

        let currentItems = [];

        function renderTable(items) {
            currentItems = items || [];
            const tbody = document.getElementById('orgTableBody');
            if (items.length === 0) {
                tbody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center py-5 text-muted">
                            <i class="fa-solid fa-folder-open fa-2x mb-2 d-block text-secondary opacity-50"></i>
                            Không có hồ sơ nào trong trạng thái này.
                        </td>
                    </tr>
                `;
                return;
            }

            let html = '';
            items.forEach(item => {
                let statusBadge = '';
                if (item.status === 'PENDING_APPROVAL') {
                    statusBadge = '<span class="badge badge-pending px-2 py-1"><i class="fa-solid fa-clock me-1"></i>Chờ duyệt</span>';
                } else if (item.status === 'APPROVED') {
                    statusBadge = '<span class="badge badge-approved px-2 py-1"><i class="fa-solid fa-check me-1"></i>Đã phê duyệt</span>';
                } else if (item.status === 'REJECTED') {
                    statusBadge = '<span class="badge badge-rejected px-2 py-1"><i class="fa-solid fa-ban me-1"></i>Đã từ chối</span>';
                } else {
                    statusBadge = `<span class="badge bg-secondary px-2 py-1">${escapeHtml(item.status)}</span>`;
                }

                let actions = '';
                if (item.status === 'PENDING_APPROVAL') {
                    actions = `
                        <div class="d-inline-flex gap-2">
                            <button class="btn btn-sm btn-dark rounded-0 px-3 py-1" data-org-action="approve" data-org-id="${escapeHtml(item.organizationId)}" data-org-name="${escapeHtml(item.name)}">
                                <i class="fa-solid fa-check me-1"></i> Phê duyệt
                            </button>
                            <button class="btn btn-sm btn-outline-danger rounded-0 px-2 py-1" data-org-action="reject" data-org-id="${escapeHtml(item.organizationId)}" data-org-name="${escapeHtml(item.name)}">
                                <i class="fa-solid fa-ban me-1"></i> Từ chối
                            </button>
                        </div>
                    `;
                } else if (item.status === 'REJECTED' && item.rejectionReason) {
                    actions = `
                        <span class="small text-danger d-block" title="${escapeHtml(item.rejectionReason)}">
                            <i class="fa-solid fa-circle-info me-1"></i> Lý do: ${escapeHtml(item.rejectionReason.length > 30 ? item.rejectionReason.substring(0, 30) + '...' : item.rejectionReason)}
                        </span>
                    `;
                } else {
                    actions = '<span class="small text-muted"><i class="fa-solid fa-circle-check text-success me-1"></i>Đang hoạt động</span>';
                }

                let eventInfoHtml = '';
                if (item.eventTitle) {
                    eventInfoHtml = `
                        <div class="mt-1 small text-primary fw-medium">
                            <i class="fa-solid fa-calendar-check me-1 text-primary"></i>
                            <span>Sự kiện khởi tạo: <strong>${escapeHtml(item.eventTitle)}</strong></span>
                            ${item.eventDate ? `<span class="text-muted ms-1">(${escapeHtml(item.eventDate)}${item.eventVenue ? ' • ' + escapeHtml(item.eventVenue) : ''})</span>` : ''}
                        </div>
                    `;
                }

                html += `
                    <tr>
                        <td>
                            <strong class="d-block text-dark">${escapeHtml(item.name)}</strong>

                            ${eventInfoHtml}
                        </td>
                        <td>
                            <span class="d-block text-dark">${escapeHtml(item.contactEmail)}</span>
                            <span class="small text-muted">${item.requesterEmail ? escapeHtml(item.requesterEmail) : ''}</span>
                        </td>
                        <td>
                            <span class="small text-dark">${formatDate(item.submittedAt)}</span>
                        </td>
                        <td>
                            ${statusBadge}
                        </td>
                        <td class="text-end">
                            ${actions}
                        </td>
                    </tr>
                `;
            });

            tbody.innerHTML = html;
        }

        function renderPagination(data) {
            const total = data.total || 0;
            const totalPages = Math.ceil(total / pageSize) || 1;
            document.getElementById('paginationInfo').textContent = `Trang ${currentPage} / ${totalPages} (Tổng ${total} hồ sơ)`;

            const prevItem = document.getElementById('prevPageItem');
            const nextItem = document.getElementById('nextPageItem');

            if (currentPage <= 1) {
                prevItem.classList.add('disabled');
            } else {
                prevItem.classList.remove('disabled');
            }

            if (currentPage >= totalPages) {
                nextItem.classList.add('disabled');
            } else {
                nextItem.classList.remove('disabled');
            }
        }

        function changePage(delta) {
            const target=currentPage+delta;if(target<1||target>Math.max(1,Math.ceil(totalRecords/pageSize)))return;currentPage=target;
            loadRequests();
        }

        function openApproveModal(orgId, orgName) {
            document.getElementById('approveOrgId').value = orgId;
            document.getElementById('approveOrgName').textContent = orgName;
            document.getElementById('approveRatePercent').value = '5.0';
            document.getElementById('approveFixedFee').value = '10000';

            const item = (currentItems || []).find(x => x.organizationId === orgId);
            const eventTitle = (item && item.eventTitle) ? item.eventTitle : 'Chưa cung cấp tên sự kiện';
            const eventTime = (item && item.eventDate) ? (item.eventDate + (item.eventStartTime ? ' (' + item.eventStartTime + (item.eventEndTime ? ' - ' + item.eventEndTime : '') + ')' : '')) : 'Chưa cung cấp lịch sự kiện';
            const eventVenue = (item && item.eventVenue) ? item.eventVenue : 'Chưa cung cấp địa điểm';

            document.getElementById('approveEventTitle').textContent = eventTitle;
            document.getElementById('approveEventTime').textContent = eventTime;
            document.getElementById('approveEventVenue').textContent = eventVenue;

            const descWrap = document.getElementById('approveDescWrap');
            const descEl = document.getElementById('approveOrgDesc');
            if (item && item.cleanDescription) {
                descWrap.style.display = 'block';
                descEl.textContent = item.cleanDescription;
            } else if (item && item.description && !item.description.startsWith('[')) {
                descWrap.style.display = 'block';
                descEl.textContent = item.description;
            } else {
                descWrap.style.display = 'none';
            }

            // Đồng thời fetch chi tiết mới nhất từ server để đồng bộ đầy đủ nhất
            fetch(`${contextPath}/api/admin/organization-requests/${orgId}`)
                .then(r => r.ok ? r.json() : null)
                .then(res => {
                    if (res && res.data) {
                        const d = res.data;
                        if (d.eventTitle) document.getElementById('approveEventTitle').textContent = d.eventTitle;
                        if (d.eventDate) {
                            const timeStr = d.eventDate + (d.eventStartTime ? ' (' + d.eventStartTime + (d.eventEndTime ? ' - ' + d.eventEndTime : '') + ')' : '');
                            document.getElementById('approveEventTime').textContent = timeStr;
                        }
                        if (d.eventVenue) document.getElementById('approveEventVenue').textContent = d.eventVenue;
                        if (d.cleanDescription) {
                            descWrap.style.display = 'block';
                            descEl.textContent = d.cleanDescription;
                        }
                    }
                })
                .catch(() => {});

            approveModal.show();
        }

        function openRejectModal(orgId, orgName) {
            document.getElementById('rejectOrgId').value = orgId;
            document.getElementById('rejectOrgName').textContent = orgName;
            document.getElementById('rejectReason').value = '';
            document.getElementById('charCounter').textContent = '0/500 ký tự';
            document.getElementById('reasonValidationMsg').textContent = '';
            rejectModal.show();
        }

        async function submitApprove(e) {
            e.preventDefault();
            const orgId = document.getElementById('approveOrgId').value;
            const ratePercent = document.getElementById('approveRatePercent').value;
            const fixedFee = document.getElementById('approveFixedFee').value;
            const effectiveDate = document.getElementById('approveEffectiveDate').value;
            const btn = document.getElementById('btnConfirmApprove');

            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang xử lý...';

            try {
                const response = await fetch(`${contextPath}/api/admin/organization-requests/${orgId}/approve`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken,
                        'Idempotency-Key': (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'idemp-' + Date.now()
                    },
                    body: JSON.stringify({
                        ratePercent: ratePercent,
                        fixedFee: fixedFee,
                        effectiveDate: effectiveDate
                    })
                });

                const result = await response.json();
                approveModal.hide();

                if (response.ok && result.data?.success === true) {
                    showAlert('success', 'Phê duyệt hồ sơ thành công. Người đại diện đã được cấp quyền quản lý tổ chức.');
                    loadRequests();
                } else {
                    showAlert('danger', 'Phê duyệt thất bại: ' + (result.data?.message || result.error?.message || 'Có lỗi xảy ra'));
                }
            } catch (err) {
                approveModal.hide();
                showAlert('danger', 'Lỗi kết nối khi phê duyệt: ' + err.message);
            } finally {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-check me-1"></i> Xác nhận phê duyệt';
            }
        }

        async function submitReject(e) {
            e.preventDefault();
            const orgId = document.getElementById('rejectOrgId').value;
            const reason = document.getElementById('rejectReason').value.trim();
            const btn = document.getElementById('btnConfirmReject');

            if (reason.length < 10 || reason.length > 500) {
                document.getElementById('reasonValidationMsg').textContent = 'Lý do từ chối phải từ 10 đến 500 ký tự.';
                return;
            }

            btn.disabled = true;
            btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang xử lý...';

            try {
                const response = await fetch(`${contextPath}/api/admin/organization-requests/${orgId}/reject`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken,
                        'Idempotency-Key': (window.crypto && crypto.randomUUID) ? crypto.randomUUID() : 'idemp-' + Date.now()
                    },
                    body: JSON.stringify({
                        reason: reason
                    })
                });

                const result = await response.json();
                rejectModal.hide();

                if (response.ok && result.data?.success === true) {
                    showAlert('success', 'Đã từ chối hồ sơ tổ chức thành công.');
                    loadRequests();
                } else {
                    showAlert('danger', 'Từ chối thất bại: ' + (result.data?.message || result.error?.message || 'Có lỗi xảy ra'));
                }
            } catch (err) {
                rejectModal.hide();
                showAlert('danger', 'Lỗi kết nối khi từ chối: ' + err.message);
            } finally {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-ban me-1"></i> Xác nhận từ chối';
            }
        }

        document.getElementById('orgTableBody').addEventListener('click', event => {
            const button = event.target.closest('button[data-org-action]');
            if (!button) return;
            const action = button.dataset.orgAction === 'approve' ? openApproveModal : openRejectModal;
            action(button.dataset.orgId, button.dataset.orgName);
        });

        // Initial load
        document.addEventListener('DOMContentLoaded', () => {
            loadRequests();
        });
