/**
 * Quản lý duyệt hồ sơ tổ chức (Admin)
 * Tư duy tuần tự: 1. Hằng số & DOM -> 2. Trạng thái -> 3. Render -> 4. Hành động -> 5. Sự kiện
 */

// 1. HẰNG SỐ & DOM
const contextPath = (typeof window !== 'undefined' && window.TC?.base) ||
    document.querySelector('meta[name="context-path"]')?.getAttribute('content') || '';
const csrfToken = document.querySelector('meta[name="csrf-token"]')?.getAttribute('content') || '';
const pageSize = 6;
const approveModal = typeof bootstrap !== 'undefined' && bootstrap.Modal ? new bootstrap.Modal(document.getElementById('approveModal')) : null;
const rejectModal = typeof bootstrap !== 'undefined' && bootstrap.Modal ? new bootstrap.Modal(document.getElementById('rejectModal')) : null;

// 2. TRẠNG THÁI
let currentStatus = 'PENDING_APPROVAL';
let currentPage = 1;
let totalRecords = 0;
let currentItems = [];
let requestGeneration = 0;
let viewGeneration = 0;
let approvalContext = null;
let rejectionContext = null;

function escapeHtml(str) {
    if (str == null) return '';
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;').replace(/'/g, '&#039;');
}

function formatDate(dateStr) {
    if (!dateStr) return 'N/A';
    try {
        return new Date(dateStr).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch {
        return dateStr;
    }
}

// 3. RENDER FUNCTIONS

function renderTable(items) {
    const tbody = document.getElementById('orgTableBody');
    if (!tbody) return;

    if (!items || items.length === 0) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-5 text-muted"><i class="fa-solid fa-inbox fa-3x mb-3 text-secondary d-block"></i>Không có hồ sơ nào trong trạng thái này.</td></tr>`;
        return;
    }

    tbody.innerHTML = items.map(org => {
        let statusBadge = '<span class="badge bg-warning text-dark"><i class="fa-regular fa-clock me-1"></i> Chờ duyệt</span>';
        let actionButtons = `
            <button type="button" class="btn btn-sm btn-outline-success me-1" data-org-action="approve" data-org-id="${escapeHtml(org.organizationId)}" data-org-name="${escapeHtml(org.name)}"><i class="fa-solid fa-check"></i> Duyệt</button>
            <button type="button" class="btn btn-sm btn-outline-danger" data-org-action="reject" data-org-id="${escapeHtml(org.organizationId)}" data-org-name="${escapeHtml(org.name)}"><i class="fa-solid fa-xmark"></i> Từ chối</button>`;

        if (org.status === 'APPROVED') {
            statusBadge = '<span class="badge bg-success"><i class="fa-solid fa-check me-1"></i> Đã duyệt</span>';
            actionButtons = '<span class="text-muted small">Đã xử lý</span>';
        } else if (org.status === 'REJECTED') {
            statusBadge = '<span class="badge bg-danger"><i class="fa-solid fa-ban me-1"></i> Đã từ chối</span>';
            actionButtons = '<span class="text-muted small">Đã xử lý</span>';
        }

        return `
            <tr>
                <td class="align-middle"><strong>${escapeHtml(org.name)}</strong>${org.contactEmail ? `<br><small class="text-muted">${escapeHtml(org.contactEmail)}</small>` : ''}</td>
                <td class="align-middle">${escapeHtml(org.representativeName || org.ownerName || 'N/A')}</td>
                <td class="align-middle">${formatDate(org.submittedAt || org.createdAt)}</td>
                <td class="align-middle">${statusBadge}</td>
                <td class="align-middle text-end">${actionButtons}</td>
            </tr>`;
    }).join('');
}

function renderPagination(data, disabled = false) {
    const total = (data && typeof data.total === 'number') ? data.total : (totalRecords || 0);
    const totalPages = Math.ceil(total / pageSize) || 1;
    const paginationInfo = document.getElementById('paginationInfo');
    if (paginationInfo) {
        paginationInfo.textContent = `Trang ${currentPage} / ${totalPages} (Tổng ${total} hồ sơ)`;
    }

    const paginationList = document.getElementById('paginationList');
    if (window.TicketUI?.renderPagination && paginationList) {
        window.TicketUI.renderPagination(paginationList, {
            page: currentPage,
            total,
            pageSize,
            disabled,
            onPageChange(page) {
                invalidateDecisions();
                currentPage = page;
                loadRequests();
            }
        });
        return;
    }

    const prevItem = document.getElementById('prevPageItem');
    const nextItem = document.getElementById('nextPageItem');
    if (prevItem) {
        if (currentPage <= 1 || disabled) prevItem.classList.add('disabled');
        else prevItem.classList.remove('disabled');
    }
    if (nextItem) {
        if (currentPage >= totalPages || disabled) nextItem.classList.add('disabled');
        else nextItem.classList.remove('disabled');
    }
}

function showAlert(type, message) {
    const placeholder = document.getElementById('alertPlaceholder');
    if (!placeholder) return;
    placeholder.innerHTML = `
        <div class="alert alert-${type} alert-dismissible fade show rounded-0 mb-4" role="alert">
            <i class="fa-solid ${type === 'success' ? 'fa-circle-check' : 'fa-triangle-exclamation'} me-2"></i>
            ${escapeHtml(message)}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>`;
    const alertEl = placeholder.querySelector('.alert');
    setTimeout(() => {
        if (!alertEl) return;
        const bsAlert = (typeof bootstrap !== 'undefined' && bootstrap.Alert) ? bootstrap.Alert.getInstance(alertEl) : null;
        if (bsAlert) bsAlert.close();
        else alertEl.remove();
    }, 6000);
}

function updateCharCount() {
    const val = document.getElementById('rejectReason')?.value || '';
    const counter = document.getElementById('charCounter');
    if (counter) counter.textContent = val.length + '/500 ký tự';
    const msgEl = document.getElementById('reasonValidationMsg');
    if (msgEl) {
        msgEl.textContent = (val.trim().length > 0 && val.trim().length < 10) ? 'Lý do phải có ít nhất 10 ký tự.' : '';
    }
}

// 4. HÀNH ĐỘNG (ACTIONS)

function isCurrentDecision(context, type) {
    return !!(context && context.view === viewGeneration &&
        context === (type === 'approve' ? approvalContext : rejectionContext));
}

function invalidateDecisions() {
    viewGeneration++;
    approvalContext = null;
    rejectionContext = null;
    if (approveModal) approveModal.hide();
    if (rejectModal) rejectModal.hide();
    const alertPlaceholder = document.getElementById('alertPlaceholder');
    if (alertPlaceholder) alertPlaceholder.innerHTML = '';
}

async function loadRequests() {
    const generation = ++requestGeneration;
    const status = currentStatus;
    const page = currentPage;
    currentItems = [];
    renderPagination({ total: totalRecords }, true);

    const tbody = document.getElementById('orgTableBody');
    if (tbody) {
        tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-muted"><div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>Đang tải danh sách hồ sơ...</td></tr>`;
    }

    let url = `${contextPath}/api/admin/organization-requests?page=${page}&pageSize=${pageSize}`;
    if (status && status !== 'ALL') url += `&status=${status}`;

    try {
        const response = await fetch(url, { method: 'GET', headers: { 'Accept': 'application/json' } });
        if (generation !== requestGeneration) return;
        if (!response.ok) throw new Error(`Máy chủ phản hồi lỗi: ${response.statusText || response.status}`);

        const res = await response.json();
        if (generation !== requestGeneration) return;

        if (res.data && Array.isArray(res.data.items) && typeof res.data.total === 'number') {
            totalRecords = res.data.total;
            currentItems = res.data.items;

            const maxPage = Math.max(1, Math.ceil(totalRecords / pageSize));
            if (currentPage > maxPage) {
                currentPage = maxPage;
                loadRequests();
                return;
            }

            renderTable(currentItems);
            renderPagination(res.data, false);
        } else {
            throw new Error('Dữ liệu trả về không đúng định dạng');
        }
    } catch (err) {
        if (generation !== requestGeneration) return;
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="5" class="text-center py-4 text-danger"><i class="fa-solid fa-triangle-exclamation fa-2x mb-2 d-block"></i>Có lỗi xảy ra khi tải dữ liệu: ${escapeHtml(err.message)}<br><button type="button" class="btn btn-sm btn-outline-primary mt-2" onclick="loadRequests()"><i class="fa-solid fa-rotate-right me-1"></i> Thử lại</button></td></tr>`;
        }
    }
}

function changePage(delta) {
    const target = currentPage + delta;
    if (target < 1 || target > Math.max(1, Math.ceil(totalRecords / pageSize))) return;
    invalidateDecisions();
    currentPage = target;
    loadRequests();
}

function openApproveModal(orgId, orgName) {
    rejectionContext = null;
    if (rejectModal) rejectModal.hide();
    const context = { orgId, view: viewGeneration, busy: false };
    approvalContext = context;

    const btn = document.getElementById('btnConfirmApprove');
    if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<i class="fa-solid fa-check me-1"></i> Xác nhận phê duyệt';
    }
    const orgIdEl = document.getElementById('approveOrgId');
    if (orgIdEl) orgIdEl.value = orgId;
    const orgNameEl = document.getElementById('approveOrgName');
    if (orgNameEl) orgNameEl.textContent = orgName;

    const item = currentItems.find(i => (i.organizationId === orgId || i.id === orgId));
    const titleEl = document.getElementById('approveEventTitle');
    if (titleEl) titleEl.textContent = item?.eventTitle || 'Chưa có thông tin sự kiện';
    const timeEl = document.getElementById('approveEventTime');
    if (timeEl) {
        timeEl.textContent = item?.eventDate ? (item.eventDate + (item.eventStartTime ? ` (${item.eventStartTime}${item.eventEndTime ? ' - ' + item.eventEndTime : ''})` : '')) : 'Chưa cung cấp lịch sự kiện';
    }
    const venueEl = document.getElementById('approveEventVenue');
    if (venueEl) venueEl.textContent = item?.eventVenue || 'Chưa cung cấp địa điểm';

    const descWrap = document.getElementById('approveDescWrap');
    const descEl = document.getElementById('approveOrgDesc');
    if (descWrap && descEl) {
        const desc = item?.cleanDescription || (item?.description && !item.description.startsWith('[') ? item.description : '');
        descWrap.style.display = desc ? 'block' : 'none';
        descEl.textContent = desc;
    }

    fetch(`${contextPath}/api/admin/organization-requests/${orgId}`)
        .then(r => r.ok ? r.json() : null)
        .then(res => {
            if (isCurrentDecision(context, 'approve') && res?.data) {
                const d = res.data;
                if (d.eventTitle && titleEl) titleEl.textContent = d.eventTitle;
                if (d.eventDate && timeEl) {
                    timeEl.textContent = d.eventDate + (d.eventStartTime ? ` (${d.eventStartTime}${d.eventEndTime ? ' - ' + d.eventEndTime : ''})` : '');
                }
                if (d.eventVenue && venueEl) venueEl.textContent = d.eventVenue;
                if (d.cleanDescription && descWrap && descEl) {
                    descWrap.style.display = 'block';
                    descEl.textContent = d.cleanDescription;
                }
            }
        })
        .catch(() => {});

    if (approveModal) approveModal.show();
}

function openRejectModal(orgId, orgName) {
    approvalContext = null;
    if (approveModal) approveModal.hide();
    rejectionContext = { orgId, view: viewGeneration, busy: false };

    const btn = document.getElementById('btnConfirmReject');
    if (btn) {
        btn.disabled = false;
        btn.innerHTML = '<i class="fa-solid fa-ban me-1"></i> Xác nhận từ chối';
    }
    const rejectOrgId = document.getElementById('rejectOrgId');
    if (rejectOrgId) rejectOrgId.value = orgId;
    const rejectOrgName = document.getElementById('rejectOrgName');
    if (rejectOrgName) rejectOrgName.textContent = orgName;
    const rejectReason = document.getElementById('rejectReason');
    if (rejectReason) rejectReason.value = '';
    const charCounter = document.getElementById('charCounter');
    if (charCounter) charCounter.textContent = '0/500 ký tự';
    const reasonValidationMsg = document.getElementById('reasonValidationMsg');
    if (reasonValidationMsg) reasonValidationMsg.textContent = '';

    if (rejectModal) rejectModal.show();
}

async function submitApprove(e) {
    if (e?.preventDefault) e.preventDefault();
    const context = approvalContext;
    if (!isCurrentDecision(context, 'approve') || context.busy) return;
    const orgId = context.orgId;
    const btn = document.getElementById('btnConfirmApprove');

    context.busy = true;
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang xử lý...';
    }

    try {
        const idempKey = (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : 'idemp-' + Date.now();
        const response = await fetch(`${contextPath}/api/admin/organization-requests/${orgId}/approve`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrfToken,
                'Idempotency-Key': idempKey
            },
            body: JSON.stringify({
                ratePercent: document.getElementById('approveRatePercent')?.value,
                fixedFee: document.getElementById('approveFixedFee')?.value,
                effectiveDate: document.getElementById('approveEffectiveDate')?.value
            })
        });

        const result = await response.json();
        if (!isCurrentDecision(context, 'approve')) return;
        if (approveModal) approveModal.hide();

        if (response.ok && result.data?.success === true) {
            showAlert('success', 'Phê duyệt hồ sơ thành công. Người đại diện đã được cấp quyền quản lý tổ chức.');
            loadRequests();
        } else {
            showAlert('danger', 'Phê duyệt thất bại: ' + (result.data?.message || result.error?.message || 'Có lỗi xảy ra'));
        }
    } catch (err) {
        if (!isCurrentDecision(context, 'approve')) return;
        if (approveModal) approveModal.hide();
        showAlert('danger', 'Lỗi kết nối khi phê duyệt: ' + err.message);
    } finally {
        if (context !== approvalContext) return;
        context.busy = false;
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-check me-1"></i> Xác nhận phê duyệt';
        }
    }
}

async function submitReject(e) {
    if (e?.preventDefault) e.preventDefault();
    const context = rejectionContext;
    if (!isCurrentDecision(context, 'reject') || context.busy) return;
    const orgId = context.orgId;
    const reason = document.getElementById('rejectReason')?.value.trim() || '';
    const btn = document.getElementById('btnConfirmReject');

    if (reason.length < 10 || reason.length > 500) {
        const msgEl = document.getElementById('reasonValidationMsg');
        if (msgEl) msgEl.textContent = 'Lý do từ chối phải từ 10 đến 500 ký tự.';
        return;
    }

    context.busy = true;
    if (btn) {
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span> Đang xử lý...';
    }

    try {
        const idempKey = (typeof crypto !== 'undefined' && crypto.randomUUID) ? crypto.randomUUID() : 'idemp-' + Date.now();
        const response = await fetch(`${contextPath}/api/admin/organization-requests/${orgId}/reject`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrfToken,
                'Idempotency-Key': idempKey
            },
            body: JSON.stringify({ reason })
        });

        const result = await response.json();
        if (!isCurrentDecision(context, 'reject')) return;
        if (rejectModal) rejectModal.hide();

        if (response.ok && result.data?.success === true) {
            showAlert('success', 'Đã từ chối hồ sơ tổ chức thành công.');
            loadRequests();
        } else {
            showAlert('danger', 'Từ chối thất bại: ' + (result.data?.message || result.error?.message || 'Có lỗi xảy ra'));
        }
    } catch (err) {
        if (!isCurrentDecision(context, 'reject')) return;
        if (rejectModal) rejectModal.hide();
        showAlert('danger', 'Lỗi kết nối khi từ chối: ' + err.message);
    } finally {
        if (context !== rejectionContext) return;
        context.busy = false;
        if (btn) {
            btn.disabled = false;
            btn.innerHTML = '<i class="fa-solid fa-ban me-1"></i> Xác nhận từ chối';
        }
    }
}

// 5. GẮN SỰ KIỆN & KHỞI TẠO (EVENTS & INITIALIZATION)

const effectiveDateEl = document.getElementById('approveEffectiveDate');
if (effectiveDateEl) effectiveDateEl.value = new Date().toISOString().split('T')[0];

document.querySelectorAll('#orgStatusTabs button').forEach(tabBtn => {
    tabBtn.addEventListener('click', () => {
        document.querySelectorAll('#orgStatusTabs button').forEach(b => b.classList.remove('active'));
        tabBtn.classList.add('active');
        invalidateDecisions();
        currentStatus = tabBtn.getAttribute('data-status') || tabBtn.getAttribute('id')?.replace('tab-', '') || 'ALL';
        currentPage = 1;
        const titleEl = document.getElementById('tableHeaderTitle');
        if (titleEl) {
            titleEl.textContent = currentStatus === 'APPROVED' ? 'Danh sách hồ sơ đã phê duyệt' :
                currentStatus === 'REJECTED' ? 'Danh sách hồ sơ đã từ chối' : 'Danh sách hồ sơ chờ duyệt';
        }
        loadRequests();
    });
});

document.getElementById('orgTableBody')?.addEventListener('click', event => {
    const button = event.target.closest('button[data-org-action]');
    if (!button) return;
    const action = button.dataset.orgAction === 'approve' ? openApproveModal : openRejectModal;
    action(button.dataset.orgId, button.dataset.orgName);
});

document.getElementById('approveModal')?.addEventListener('hide.bs.modal', () => { approvalContext = null; });
document.getElementById('rejectModal')?.addEventListener('hide.bs.modal', () => { rejectionContext = null; });
document.getElementById('rejectReason')?.addEventListener('input', updateCharCount);
document.getElementById('approveForm')?.addEventListener('submit', submitApprove);
document.getElementById('rejectForm')?.addEventListener('submit', submitReject);

document.addEventListener('DOMContentLoaded', () => { loadRequests(); });

// Gán vào context global để tương thích với Node test runner (vm.runInContext)
if (typeof this !== 'undefined') {
    this.loadRequests = loadRequests;
    this.renderTable = renderTable;
    this.showAlert = showAlert;
    this.openApproveModal = openApproveModal;
    this.openRejectModal = openRejectModal;
    this.submitApprove = submitApprove;
    this.submitReject = submitReject;
    this.isCurrentDecision = isCurrentDecision;
    this.invalidateDecisions = invalidateDecisions;
    this.changePage = changePage;
}
