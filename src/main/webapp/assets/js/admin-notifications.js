/**
 * Chuông thông báo và phê duyệt nhanh hồ sơ tổ chức (Admin)
 * Tư duy tuần tự: 1. Hằng số & DOM -> 2. Trạng thái -> 3. Render -> 4. Hành động -> 5. Sự kiện
 */
(function() {
    'use strict';

    // 1. HẰNG SỐ & DOM HELPERS
    function getContextPath() {
        return window.TC_CONTEXT_PATH ||
            document.querySelector('meta[name="context-path"]')?.getAttribute('content') ||
            (window.location?.pathname?.startsWith('/ticketscenter') ? '/ticketscenter' : '');
    }

    function getCsrfToken() {
        return document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') ||
            document.querySelector('input[name="_csrf"]')?.value ||
            '';
    }

    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    function formatDate(instantStr) {
        if (!instantStr) return '';
        try {
            const date = new Date(instantStr);
            return date.toLocaleDateString('vi-VN') + ' ' + date.toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch {
            return instantStr;
        }
    }

    function showAdminToast(message, type = 'success') {
        let container = document.getElementById('admin-toast-container');
        if (!container) {
            container = document.createElement('div');
            container.id = 'admin-toast-container';
            container.className = 'toast-container position-fixed top-0 end-0 p-3';
            container.style.zIndex = '1095';
            document.body.appendChild(container);
        }

        const toastEl = document.createElement('div');
        toastEl.className = 'toast align-items-center text-bg-' + type + ' border-0 show shadow-sm';
        toastEl.setAttribute('role', 'alert');
        toastEl.setAttribute('aria-live', 'assertive');
        toastEl.setAttribute('aria-atomic', 'true');
        toastEl.innerHTML = `
            <div class="d-flex">
                <div class="toast-body small fw-semibold">${escapeHtml(message)}</div>
                <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast" aria-label="Close"></button>
            </div>
        `;

        container.appendChild(toastEl);
        setTimeout(() => {
            toastEl.classList.remove('show');
            setTimeout(() => toastEl.remove(), 300);
        }, 4000);
    }

    // 2. RENDER FUNCTIONS

    function loadOrgApprovalTable(items) {
        const tbody = document.getElementById('pendingOrgTableBody');
        const titleCount = document.getElementById('pendingOrgTitleCount');
        if (!tbody) return;

        if (titleCount) {
            titleCount.textContent = items.length + ' hồ sơ';
        }

        if (!items || items.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4 text-muted">Không có hồ sơ nào đang chờ xét duyệt.</td></tr>';
            return;
        }

        tbody.innerHTML = '';
        items.forEach(item => {
            const tr = document.createElement('tr');
            tr.setAttribute('data-org-id', item.organizationId);

            let scheduleHtml = '';
            if (item.eventDate) {
                const time = (item.eventStartTime || '') + (item.eventEndTime ? (' - ' + item.eventEndTime) : '');
                scheduleHtml = `<div class="small text-secondary mt-1"><i class="fa-regular fa-calendar me-1"></i>${escapeHtml(item.eventDate + (time ? ' (' + time + ')' : ''))}</div>`;
            } else if (item.description && item.description.includes('[Lịch trình')) {
                const match = item.description.match(/\[Lịch trình[^\]]*\]\s*([^|\n]+(?:\|\s*[^|\n]+)*)/);
                if (match) {
                    scheduleHtml = `<div class="small text-secondary mt-1"><i class="fa-regular fa-calendar me-1"></i>${escapeHtml(match[1].trim())}</div>`;
                }
            }

            tr.innerHTML = `
                <td>
                    <strong class="d-block text-dark">${escapeHtml(item.name || 'Hồ sơ đối tác')}</strong>
                    <span class="small text-muted">Organization: ${escapeHtml(item.organizationId)}</span>
                    ${scheduleHtml}
                </td>
                <td><span class="d-block text-dark">${escapeHtml(item.contactEmail || '-')}</span></td>
                <td>${formatDate(item.submittedAt)}</td>
                <td><span class="small text-muted">${item.description ? escapeHtml(item.description.split('\n')[0]) : '-'}</span></td>
                <td>
                    <select class="form-select form-select-sm rounded-0 border-secondary-subtle" style="width: 110px;">
                        <option value="5">5.0%</option>
                        <option value="8" selected>8.0%</option>
                        <option value="10">10.0%</option>
                    </select>
                </td>
                <td class="text-end">
                    <div class="d-inline-flex gap-2">
                        <button class="btn btn-sm btn-dark rounded-0 px-3 py-1" onclick="approveFromNotification(event, '${escapeHtml(item.organizationId)}')">Phê duyệt</button>
                    </div>
                </td>
            `;
            tbody.appendChild(tr);
        });
    }

    // 3. HÀNH ĐỘNG (ACTIONS)

    async function approveFromNotification(event, organizationId) {
        if (event) {
            event.preventDefault();
            event.stopPropagation();
        }

        const button = event ? event.currentTarget : null;
        if (button) {
            button.disabled = true;
            button.innerHTML = '<span class="spinner-border spinner-border-sm" role="status"></span>';
        }

        const contextPath = getContextPath();
        const csrfToken = getCsrfToken();

        try {
            const response = await fetch(`${contextPath}/api/admin/organization-requests/${encodeURIComponent(organizationId)}/approve`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfToken,
                    'Accept': 'application/json'
                }
            });

            if (!response.ok) {
                const errData = await response.json().catch(() => ({}));
                const errMsg = errData?.error?.message || 'Không thể phê duyệt yêu cầu.';
                showAdminToast(errMsg, 'danger');
                if (button) {
                    button.disabled = false;
                    button.textContent = 'Duyệt';
                }
                return;
            }

            const itemEl = document.querySelector(`.admin-notification-item[data-org-id="${organizationId}"]`);
            if (itemEl) itemEl.remove();

            const badge = document.getElementById('adminNotificationBadge');
            const countText = document.getElementById('adminNotificationCountText');
            const emptyEl = document.getElementById('adminNotificationEmpty');

            let currentCount = badge ? parseInt(badge.textContent || '0', 10) : 0;
            currentCount = Math.max(0, currentCount - 1);

            if (badge) {
                badge.textContent = currentCount;
                if (currentCount <= 0) badge.classList.add('d-none');
            }
            if (countText) countText.textContent = currentCount;
            if (emptyEl && currentCount <= 0) emptyEl.style.display = '';

            showAdminToast('Đã phê duyệt yêu cầu và cập nhật lịch sự kiện thành công.', 'success');

            // Finance audit/report tables may also contain this organization UUID.
            const tableRow = document.querySelector(`#pendingOrgTableBody tr[data-org-id="${organizationId}"]`);
            if (tableRow) {
                tableRow.remove();
                const tbody = document.getElementById('pendingOrgTableBody');
                if (tbody && tbody.querySelectorAll('tr[data-org-id]').length === 0) {
                    tbody.innerHTML = '<tr><td colspan="6" class="text-center py-4 text-muted">Không có hồ sơ nào đang chờ xét duyệt.</td></tr>';
                }
            } else {
                const allRows = document.querySelectorAll('#pendingOrgTableBody tr');
                for (const r of allRows) {
                    if (r.textContent.includes(organizationId)) {
                        r.remove();
                        break;
                    }
                }
            }

            const titleCount = document.getElementById('pendingOrgTitleCount');
            if (titleCount) titleCount.textContent = currentCount + ' hồ sơ';
        } catch (err) {
            console.error('Approve error:', err);
            showAdminToast('Lỗi kết nối khi phê duyệt yêu cầu.', 'danger');
            if (button) {
                button.disabled = false;
                button.textContent = 'Duyệt';
            }
        }
    }

    async function loadAdminNotifications() {
        const badge = document.getElementById('adminNotificationBadge');
        const countText = document.getElementById('adminNotificationCountText');
        const listContainer = document.getElementById('adminNotificationList');
        const emptyEl = document.getElementById('adminNotificationEmpty');

        if (!badge || !listContainer) return;
        const contextPath = getContextPath();

        try {
            const response = await fetch(`${contextPath}/api/admin/organization-requests/pending`, {
                headers: { 'Accept': 'application/json' }
            });

            if (!response.ok) return;

            const resJson = await response.json();
            const dataObj = resJson.data || resJson;
            const items = Array.isArray(dataObj) ? dataObj : (dataObj.items || []);
            const count = (typeof dataObj.count === 'number') ? dataObj.count : items.length;

            if (document.getElementById('pendingOrgTableBody')) {
                loadOrgApprovalTable(items);
            }

            if (count > 0) {
                badge.textContent = count;
                badge.classList.remove('d-none');
                if (countText) countText.textContent = count;
                if (emptyEl) emptyEl.style.display = 'none';

                listContainer.querySelectorAll('.admin-notification-item').forEach(el => el.remove());

                items.forEach(item => {
                    let scheduleInfo = '';
                    if (item.eventDate) {
                        const time = (item.eventStartTime || '') + (item.eventEndTime ? (' - ' + item.eventEndTime) : '');
                        scheduleInfo = item.eventDate + (time ? ' (' + time + ')' : '');
                    } else if (item.description && item.description.includes('[Lịch trình')) {
                        const match = item.description.match(/\[Lịch trình[^\]]*\]\s*([^|\n]+(?:\|\s*[^|\n]+)*)/);
                        if (match) scheduleInfo = match[1].trim();
                    }

                    const li = document.createElement('li');
                    li.className = 'p-3 border-bottom text-dark admin-notification-item';
                    li.setAttribute('data-org-id', item.organizationId);

                    let html = `
                        <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
                            <strong class="small text-dark text-truncate">${escapeHtml(item.name || 'Hồ sơ đối tác')}</strong>
                            <button class="btn btn-sm btn-success rounded-0 py-1 px-2 flex-shrink-0" style="font-size: 0.75rem;" onclick="approveFromNotification(event, '${escapeHtml(item.organizationId)}')">Duyệt</button>
                        </div>
                    `;

                    if (item.contactEmail) {
                        html += `<div class="small text-muted"><i class="fa-regular fa-envelope me-1"></i>${escapeHtml(item.contactEmail)}</div>`;
                    }
                    if (scheduleInfo) {
                        html += `<div class="small text-secondary mt-1"><i class="fa-regular fa-calendar me-1"></i>${escapeHtml(scheduleInfo)}</div>`;
                    }
                    if (item.submittedAt) {
                        html += `<div class="text-muted mt-1" style="font-size: 0.7rem;"><i class="fa-regular fa-clock me-1"></i>${formatDate(item.submittedAt)}</div>`;
                    }

                    li.innerHTML = html;
                    listContainer.appendChild(li);
                });
            } else {
                badge.classList.add('d-none');
                if (countText) countText.textContent = '0';
                if (emptyEl) emptyEl.style.display = '';
            }
        } catch (err) {
            console.error('Failed to load admin notifications:', err);
        }
    }

    // 4. GẮN SỰ KIỆN & KHỞI TẠO (EVENTS & INITIALIZATION)
    window.approveFromNotification = approveFromNotification;

    document.addEventListener('DOMContentLoaded', () => {
        if (document.getElementById('adminNotificationBell')) {
            loadAdminNotifications();
        }
    });
})();
