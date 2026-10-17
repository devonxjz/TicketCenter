<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">

<head>
    <title>Sự kiện của tổ chức | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css?v=20261014">
    <style>
        [hidden] {
            display: none !important
        }
    </style>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
</head>

<body class="bg-light tc-organizer-page" data-context="${pageContext.request.contextPath}" data-dong-page="list">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
        <main class="dashboard-content">
            <div class="d-flex flex-wrap justify-content-between gap-3 mb-5">
                <div>
                    <h1 class="h3 fw-bold mb-1 text-dark">Sự kiện của tổ chức</h1>
                    <p class="text-muted mb-0">Tạo hồ sơ, thiết lập khu/ghế và theo dõi trạng thái duyệt.</p>
                </div>
                <a id="create-event" hidden class="btn-primary-custom align-self-start">Tạo sự kiện</a>
            </div>
            <div class="panel-box">
                <div class="panel-header">
                    <span class="panel-title">Tất cả chương trình</span>
                </div>
                <form id="filters" method="post" class="row g-3 mb-4">
                    <div class="col-md-6">
                        <label class="filter-label" for="organization">Tổ chức đang quản
                            lý</label>
                        <select id="organization" class="form-select rounded-0"></select>
                    </div>
                    <div class="col-md-4">
                        <label class="filter-label" for="filter-status">Trạng thái</label>
                        <select id="filter-status" class="form-select rounded-0">
                            <option value="">Tất cả</option>
                            <option value="DRAFT">Bản nháp</option>
                            <option value="PENDING_APPROVAL">Chờ duyệt</option>
                            <option value="REJECTED">Bị từ chối</option>
                            <option value="PUBLISHED">Đã công khai</option>
                            <option value="CANCELLED">Đã hủy</option>
                        </select>
                    </div>
                    <div class="col-md-2 d-flex align-items-end">
                        <button class="btn btn-dark w-100">Lọc</button>
                    </div>
                </form>
                <p id="status" role="status" aria-live="polite">Đang tải…</p>
                <div class="table-responsive">
                    <table class="table-custom">
                        <thead>
                            <tr>
                                <th>Ảnh bìa</th>
                                <th>Sự kiện</th>
                                <th>Trạng thái</th>
                                <th>Thời gian</th>
                                <th>Sức chứa</th>
                                <th>Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="events"></tbody>
                    </table>
                </div>
            </div>
            <nav class="d-flex justify-content-center gap-3 align-items-center mt-4" aria-label="Phân trang">
                <button id="previous" class="btn btn-outline-dark" disabled>Trang trước</button>
                <span id="page-label"></span>
                <button id="next" class="btn btn-outline-dark" disabled>Trang
                    sau</button>
            </nav>
        </main>
    </div>
    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
    <script defer src="${pageContext.request.contextPath}/assets/js/dong-events.js"></script>
</body>

</html>
