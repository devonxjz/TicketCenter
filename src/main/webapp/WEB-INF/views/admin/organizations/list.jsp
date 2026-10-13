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
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/production-ui.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-ui.css">
</head>
<body class="bg-light min-h-screen admin-workspace">

    <jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />

    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp"><jsp:param name="active" value="org-approval" /></jsp:include>

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
                                    <i class="fa-solid fa-calendar-check text-primary me-1"></i> Thông tin sự kiện dự kiến:
                                </span>
                                <span id="approveEventStatusBadge" class="badge bg-primary-subtle text-primary border small">Thông tin đăng ký</span>
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

    <script src="${pageContext.request.contextPath}/assets/js/admin-organizations.js"></script>
</body>
</html>
