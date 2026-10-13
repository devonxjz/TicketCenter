<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="activeTab" value="members" scope="request" />
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="csrf-token" content="${sessionScope.TC_CSRF_TOKEN}">
    <meta name="context-path" content="${pageContext.request.contextPath}">
    <title>Nhân Sự & Phân Quyền Tổ Chức — TicketsCenter Partner</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
    <style>
        .table-custom th {
            background-color: #f8f9fa;
            font-weight: 600;
            font-size: 0.85rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            border-bottom: 2px solid #dee2e6;
        }
        .badge-manager {
            background-color: #f8d7da;
            color: #842029;
            border: 1px solid #f5c2c7;
        }
        .badge-staff {
            background-color: #cff4fc;
            color: #055160;
            border: 1px solid #b6effb;
        }
        .badge-active {
            background-color: #d1e7dd;
            color: #0f5132;
            border: 1px solid #badbcc;
        }
        .badge-inactive {
            background-color: #e2e3e5;
            color: #41464b;
            border: 1px solid #d3d6d8;
        }
        .badge-locked {
            background-color: #fff3cd;
            color: #664d03;
            border: 1px solid #ffecb5;
        }
    </style>
</head>
<body class="bg-light min-h-screen">

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
                        <c:out value="${sessionScope.TC_CURRENT_USER.email()}" default="Thành viên" />
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
                    <h1 class="h4 fw-bold mb-1 text-dark">Nhân Sự & Phân Quyền Tổ Chức</h1>
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
            <div class="p-3 bg-white border border-danger-subtle text-danger small mb-4 shadow-sm">
                <i class="fa-solid fa-shield-halved me-2"></i> <strong>Lưu ý quản trị:</strong> 
                Tổ chức cần duy trì tối thiểu 01 Quản lý đang hoạt động. Hệ thống không cho phép khóa hoặc hạ quyền tài khoản Quản lý duy nhất còn lại của tổ chức.
            </div>

            <!-- Filter & Search Toolbar -->
            <div class="row g-2 mb-4 align-items-center">
                <div class="col-md-5">
                    <div class="input-group input-group-sm">
                        <span class="input-group-text bg-white border-secondary-subtle rounded-0"><i class="fa-solid fa-magnifying-glass text-muted"></i></span>
                        <input type="text" id="memberSearchInput" class="form-control rounded-0 border-secondary-subtle" placeholder="Tìm theo họ tên, tên đăng nhập, email...">
                    </div>
                </div>
                <div class="col-md-3">
                    <select id="memberRoleFilter" class="form-select form-select-sm rounded-0 border-secondary-subtle">
                        <option value="ALL">Tất cả vai trò</option>
                        <option value="MANAGER">Quản lý tổ chức (MANAGER)</option>
                        <option value="CHECK_IN_STAFF">Soát vé tại cửa (CHECK_IN_STAFF)</option>
                    </select>
                </div>
                <div class="col-md-2">
                    <select id="memberStatusFilter" class="form-select form-select-sm rounded-0 border-secondary-subtle">
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
            <div class="panel-box bg-white mb-5 shadow-sm border">
                <div class="panel-header d-flex justify-content-between align-items-center p-3 border-bottom">
                    <div>
                        <span class="panel-title fw-bold h6 mb-0" id="memberTableTitle">Danh sách thành viên hiện tại</span>
                        <span class="small text-muted d-block" id="memberTableSubtitle">Đang kết nối dữ liệu...</span>
                    </div>
                </div>

                <div class="table-responsive">
                    <table class="table table-hover align-middle mb-0 table-custom">
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
                            <label for="addUserName" class="form-label small fw-bold">Tên đăng nhập (userName) <span class="text-danger">*</span></label>
                            <input type="text" id="addUserName" name="userName" class="form-control rounded-0 border-secondary-subtle" required placeholder="vd: hoangphuc" autocomplete="off">
                            <div class="form-text small text-muted">Nhập chính xác userName tài khoản đã đăng ký trên hệ thống TicketsCenter.</div>
                        </div>

                        <div class="mb-3">
                            <label for="addMemberRole" class="form-label small fw-bold">Vai trò bổ nhiệm <span class="text-danger">*</span></label>
                            <select id="addMemberRole" name="role" class="form-select rounded-0 border-secondary-subtle" required>
                                <option value="CHECK_IN_STAFF" selected>Nhân viên soát vé tại cửa (CHECK_IN_STAFF)</option>
                                <option value="MANAGER">Quản lý toàn quyền tổ chức (MANAGER)</option>
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
                                <option value="CHECK_IN_STAFF">Nhân viên soát vé tại cửa (CHECK_IN_STAFF)</option>
                                <option value="MANAGER">Quản lý toàn quyền tổ chức (MANAGER)</option>
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

    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>

    <script>
        window.TC_CONTEXT_PATH = '${pageContext.request.contextPath}';
    </script>
    <script src="${pageContext.request.contextPath}/assets/js/organization-members.js"></script>
</body>
</html>
