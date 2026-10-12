<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${sessionScope.TC_CSRF_TOKEN}">
    <meta name="context-path" content="${pageContext.request.contextPath}">
    <title>Duyệt & Quản Lý Hồ Sơ Tổ Chức | TicketsCenter Admin</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
    <style>
        .nav-tabs .nav-link {
            color: #495057;
            font-weight: 500;
            border-radius: 0;
        }
        .nav-tabs .nav-link.active {
            color: #000;
            font-weight: 600;
            border-bottom: 2px solid #000;
        }
        .table-custom th {
            background-color: #f8f9fa;
            font-weight: 600;
            font-size: 0.85rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
        }
        .badge-pending {
            background-color: #fff3cd;
            color: #856404;
            border: 1px solid #ffeeba;
        }
        .badge-approved {
            background-color: #d4edda;
            color: #155724;
            border: 1px solid #c3e6cb;
        }
        .badge-rejected {
            background-color: #f8d7da;
            color: #721c24;
            border: 1px solid #f5c6cb;
        }
    </style>
</head>
<body class="bg-light min-h-screen">

    <jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />

    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp" />

        <main class="dashboard-content">
            <div class="d-flex flex-column flex-sm-row justify-content-between align-items-sm-center pb-4 mb-4 border-bottom">
                <div>
                    <h1 class="h4 fw-bold mb-1 text-dark">Thẩm Định Hồ Sơ Đối Tác Ban Tổ Chức</h1>
                    <p class="small text-muted mb-0">Duyệt hồ sơ tổ chức, thiết lập chính sách phí hoa hồng và tự động cấp quyền Manager cho người đại diện.</p>
                </div>
            </div>

            <!-- Toast / Alert Notification Container -->
            <div id="alertPlaceholder"></div>

            <!-- Rule Notice Banner -->
            <div class="p-3 bg-white border border-secondary-subtle small mb-4">
                <strong>Quy trình phê duyệt:</strong> Khi Quản trị viên chấp thuận hồ sơ, tài khoản người đại diện sẽ lập tức được cấp quyền <em>Quản lý tổ chức (Manager)</em>, khởi tạo chính sách hoa hồng sàn và cấp quyền tạo sự kiện.
            </div>

            <!-- Tabs -->
            <ul class="nav nav-tabs mb-4" id="orgStatusTabs" role="tablist">
                <li class="nav-item" role="presentation">
                    <button class="nav-link active" data-status="PENDING_APPROVAL" type="button">
                        <i class="fa-solid fa-clock-rotate-left me-1"></i> Chờ duyệt
                        <span class="badge bg-danger rounded-pill ms-1" id="tabBadgePending">0</span>
                    </button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-status="APPROVED" type="button">
                        <i class="fa-solid fa-circle-check me-1 text-success"></i> Đã phê duyệt
                    </button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-status="REJECTED" type="button">
                        <i class="fa-solid fa-circle-xmark me-1 text-danger"></i> Đã từ chối
                    </button>
                </li>
                <li class="nav-item" role="presentation">
                    <button class="nav-link" data-status="ALL" type="button">
                        <i class="fa-solid fa-list me-1"></i> Tất cả hồ sơ
                    </button>
                </li>
            </ul>

            <!-- Panel Box with Table -->
            <div class="panel-box bg-white mb-5 shadow-sm border">
                <div class="panel-header d-flex justify-content-between align-items-center p-3 border-bottom">
                    <div>
                        <h2 class="panel-title h6 fw-bold mb-0" id="tableHeaderTitle">Danh sách hồ sơ chờ xét duyệt</h2>
                        <span class="small text-muted" id="tableHeaderSubtitle">Đang tải dữ liệu...</span>
                    </div>
                    <div>
                        <button class="btn btn-sm btn-outline-secondary rounded-0" onclick="loadRequests()">
                            <i class="fa-solid fa-arrows-rotate me-1"></i> Làm mới
                        </button>
                    </div>
                </div>

                <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0 table-custom">
                        <thead>
                            <tr>
                                <th style="width: 28%;">Tên tổ chức & Mã hồ sơ</th>
                                <th style="width: 22%;">Người đại diện & Email</th>
                                <th style="width: 15%;">Ngày nộp</th>
                                <th style="width: 15%;">Trạng thái</th>
                                <th style="width: 20%;" class="text-end">Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="orgTableBody">
                            <tr>
                                <td colspan="5" class="text-center py-4 text-muted">
                                    <div class="spinner-border spinner-border-sm text-secondary me-2" role="status"></div>
                                    Đang tải dữ liệu từ máy chủ...
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>

                <!-- Pagination Footer -->
                <div class="p-3 border-top d-flex justify-content-between align-items-center">
                    <span class="small text-muted" id="paginationInfo">Trang 1 / 1 (Tổng 0 hồ sơ)</span>
                    <nav aria-label="Page navigation">
                        <ul class="pagination pagination-sm mb-0" id="paginationList">
                            <li class="page-item disabled" id="prevPageItem">
                                <a class="page-link rounded-0" href="#" onclick="changePage(-1); return false;">Trước</a>
                            </li>
                            <li class="page-item disabled" id="nextPageItem">
                                <a class="page-link rounded-0" href="#" onclick="changePage(1); return false;">Sau</a>
                            </li>
                        </ul>
                    </nav>
                </div>
            </div>

        </main>
    </div>

    <!-- Modal Phê Duyệt -->
    <div class="modal fade" id="approveModal" tabindex="-1" aria-labelledby="approveModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content rounded-0">
                <div class="modal-header border-bottom">
                    <h5 class="modal-title fw-bold" id="approveModalLabel">
                        <i class="fa-solid fa-circle-check text-success me-1"></i> Phê duyệt hồ sơ tổ chức
                    </h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <form id="approveForm" onsubmit="submitApprove(event)">
                    <div class="modal-body">
                        <input type="hidden" id="approveOrgId">
                        <p class="small text-muted mb-3">
                            Phê duyệt hồ sơ: <strong id="approveOrgName" class="text-dark"></strong>
                        </p>

                        <!-- Card thông tin Sự kiện khởi tạo và Tổ chức -->
                        <div class="card bg-light border p-3 mb-3" id="approveEventSection">
                            <div class="d-flex justify-content-between align-items-center mb-2">
                                <span class="fw-bold text-dark small text-uppercase">
                                    <i class="fa-solid fa-calendar-check text-primary me-1"></i> Sự kiện khởi tạo (Lưu vào DBS - tc_events):
                                </span>
                                <span id="approveEventStatusBadge" class="badge bg-primary-subtle text-primary border small">Khởi tạo tự động</span>
                            </div>
                            <div id="approveEventDetails" class="small">
                                <div class="mb-1"><span class="text-muted">Tên sự kiện:</span> <strong id="approveEventTitle" class="text-dark">—</strong></div>
                                <div class="mb-1"><span class="text-muted">Thời gian:</span> <span id="approveEventTime" class="text-dark">—</span></div>
                                <div class="mb-1"><span class="text-muted">Địa điểm:</span> <span id="approveEventVenue" class="text-dark">—</span></div>
                                <div id="approveDescWrap" class="mt-2 pt-2 border-top text-muted" style="display:none;">
                                    <span class="d-block text-secondary small fw-semibold">Giới thiệu tổ chức:</span>
                                    <span id="approveOrgDesc" class="d-block small text-dark fst-italic"></span>
                                </div>
                            </div>
                        </div>

                        <div class="mb-3">
                            <label for="approveRatePercent" class="form-label small fw-semibold">Tỷ lệ hoa hồng (%) <span class="text-danger">*</span></label>
                            <div class="input-group input-group-sm">
                                <input type="number" step="0.1" min="0" max="100" class="form-control rounded-0" id="approveRatePercent" value="5.0" required>
                                <span class="input-group-text rounded-0">%</span>
                            </div>
                            <div class="form-text small">Tỷ lệ trích hoa hồng trên mỗi vé được bán thành công (mặc định 5.0%).</div>
                        </div>

                        <div class="mb-3">
                            <label for="approveFixedFee" class="form-label small fw-semibold">Phí cố định mỗi vé (VNĐ) <span class="text-danger">*</span></label>
                            <div class="input-group input-group-sm">
                                <input type="number" step="1000" min="0" class="form-control rounded-0" id="approveFixedFee" value="10000" required>
                                <span class="input-group-text rounded-0">VND</span>
                            </div>
                            <div class="form-text small">Phí dịch vụ cố định áp dụng cho từng giao dịch vé (mặc định 10.000 VND).</div>
                        </div>

                        <div class="mb-3">
                            <label for="approveEffectiveDate" class="form-label small fw-semibold">Ngày bắt đầu hiệu lực</label>
                            <input type="date" class="form-control form-control-sm rounded-0" id="approveEffectiveDate">
                            <div class="form-text small">Để trống để có hiệu lực ngay tại thời điểm phê duyệt.</div>
                        </div>
                    </div>
                    <div class="modal-footer border-top">
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-0 px-3" data-bs-dismiss="modal">Hủy bỏ</button>
                        <button type="submit" class="btn btn-sm btn-dark rounded-0 px-3" id="btnConfirmApprove">
                            <i class="fa-solid fa-check me-1"></i> Xác nhận phê duyệt
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <!-- Modal Từ Chối -->
    <div class="modal fade" id="rejectModal" tabindex="-1" aria-labelledby="rejectModalLabel" aria-hidden="true">
        <div class="modal-dialog">
            <div class="modal-content rounded-0">
                <div class="modal-header border-bottom">
                    <h5 class="modal-title fw-bold" id="rejectModalLabel">
                        <i class="fa-solid fa-circle-xmark text-danger me-1"></i> Từ chối hồ sơ tổ chức
                    </h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
                </div>
                <form id="rejectForm" onsubmit="submitReject(event)">
                    <div class="modal-body">
                        <input type="hidden" id="rejectOrgId">
                        <p class="small text-muted mb-3">
                            Từ chối hồ sơ: <strong id="rejectOrgName" class="text-dark"></strong>
                        </p>

                        <div class="mb-3">
                            <label for="rejectReason" class="form-label small fw-semibold">Lý do từ chối <span class="text-danger">*</span></label>
                            <textarea class="form-control rounded-0" id="rejectReason" rows="4" minlength="10" maxlength="500" required placeholder="Nhập lý do chi tiết từ chối hồ sơ (từ 10 đến 500 ký tự)..." oninput="updateCharCount()"></textarea>
                            <div class="d-flex justify-content-between align-items-center mt-1">
                                <span class="form-text text-danger small" id="reasonValidationMsg"></span>
                                <span class="small text-muted" id="charCounter">0/500 ký tự</span>
                            </div>
                        </div>
                    </div>
                    <div class="modal-footer border-top">
                        <button type="button" class="btn btn-sm btn-outline-secondary rounded-0 px-3" data-bs-dismiss="modal">Hủy bỏ</button>
                        <button type="submit" class="btn btn-sm btn-danger rounded-0 px-3" id="btnConfirmReject">
                            <i class="fa-solid fa-ban me-1"></i> Xác nhận từ chối
                        </button>
                    </div>
                </form>
            </div>
        </div>
    </div>

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
    <script src="${pageContext.request.contextPath}/assets/js/admin-notifications.js"></script>

    <script>
        const contextPath = document.querySelector('meta[name="context-path"]').getAttribute('content') || '';
        const csrfToken = document.querySelector('meta[name="csrf-token"]').getAttribute('content') || '';

        let currentStatus = 'PENDING_APPROVAL';
        let currentPage = 1;
        const pageSize = 10;
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
                    ${message}
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

                const data = await response.json();
                const items = data.items || [];
                totalRecords = data.total || 0;

                updateHeaderAndBadges(data);
                renderTable(items);
                renderPagination(data);
            } catch (err) {
                console.error(err);
                tbody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center py-4 text-danger">
                            <i class="fa-solid fa-circle-exclamation me-1"></i> Có lỗi xảy ra khi tải dữ liệu: ${err.message}
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
            if (!text) return '';
            return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
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
                    statusBadge = `<span class="badge bg-secondary px-2 py-1">${item.status}</span>`;
                }

                let actions = '';
                if (item.status === 'PENDING_APPROVAL') {
                    actions = `
                        <div class="d-inline-flex gap-2">
                            <button class="btn btn-sm btn-dark rounded-0 px-3 py-1" onclick="openApproveModal('${item.organizationId}', '${escapeHtml(item.name)}')">
                                <i class="fa-solid fa-check me-1"></i> Phê duyệt
                            </button>
                            <button class="btn btn-sm btn-outline-danger rounded-0 px-2 py-1" onclick="openRejectModal('${item.organizationId}', '${escapeHtml(item.name)}')">
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
                            <span class="small text-muted font-monospace">${item.organizationId}</span>
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
            currentPage += delta;
            loadRequests();
        }

        function openApproveModal(orgId, orgName) {
            document.getElementById('approveOrgId').value = orgId;
            document.getElementById('approveOrgName').textContent = orgName;
            document.getElementById('approveRatePercent').value = '5.0';
            document.getElementById('approveFixedFee').value = '10000';

            const item = (currentItems || []).find(x => x.organizationId === orgId);
            const eventTitle = (item && item.eventTitle) ? item.eventTitle : ('Sự kiện khởi tạo - ' + orgName);
            const eventTime = (item && item.eventDate) ? (item.eventDate + (item.eventStartTime ? ' (' + item.eventStartTime + (item.eventEndTime ? ' - ' + item.eventEndTime : '') + ')' : '')) : 'Theo lịch trình đăng ký';
            const eventVenue = (item && item.eventVenue) ? item.eventVenue : 'Địa điểm theo thông tin đăng ký';

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

                if (response.ok && result.success) {
                    showAlert('success', 'Phê duyệt hồ sơ thành công! Đã cấp quyền Manager cho người đại diện và tạo quy tắc phí.');
                    loadRequests();
                } else {
                    showAlert('danger', 'Phê duyệt thất bại: ' + (result.message || (result.error && result.error.message) || 'Có lỗi xảy ra'));
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

                if (response.ok && result.success) {
                    showAlert('success', 'Đã từ chối hồ sơ tổ chức thành công.');
                    loadRequests();
                } else {
                    showAlert('danger', 'Từ chối thất bại: ' + (result.message || (result.error && result.error.message) || 'Có lỗi xảy ra'));
                }
            } catch (err) {
                rejectModal.hide();
                showAlert('danger', 'Lỗi kết nối khi từ chối: ' + err.message);
            } finally {
                btn.disabled = false;
                btn.innerHTML = '<i class="fa-solid fa-ban me-1"></i> Xác nhận từ chối';
            }
        }

        // Initial load
        document.addEventListener('DOMContentLoaded', () => {
            loadRequests();
        });
    </script>
</body>
</html>
