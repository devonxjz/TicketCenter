<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    response.setHeader("Cache-Control", "no-store");
%>
<!DOCTYPE html>
<html lang="vi">

<head>
    <title>Tổng quan tổ chức | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css?v=20261014">
    <style>
        [hidden] {
            display: none !important
        }
    </style>
</head>

<body class="bg-light tc-organizer-page" data-live-page="organizer-dashboard"
    data-context="${pageContext.request.contextPath}">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
        <main class="dashboard-content">
            <div class="d-flex flex-column flex-sm-row justify-content-between align-items-sm-end gap-3 mb-5">
                <div>
                    <h1 class="h3 fw-bold mb-1 text-dark">Tổng quan – <span id="org-name"></span></h1>
                    <p class="small text-muted mb-0" id="metric-asof"></p>
                </div>
                <div class="d-flex gap-2 align-items-end">
                    <div>
                        <label class="form-label small mb-1" for="organization">Tổ chức</label>
                        <select id="organization" class="form-select form-select-sm rounded-0"></select>
                    </div>
                    <a id="create-event" hidden class="btn-primary-custom">Tạo sự kiện mới</a>
                </div>
            </div>
            <p id="live-status" role="status" aria-live="polite" class="text-muted">Đang tải số liệu…</p>
            <div class="organizer-metrics mb-5">
                <div class="organizer-metric">
                    <div class="panel-box h-100 mb-0 p-4">
                        <span class="filter-label d-block mb-1">Sự
                            kiện</span><span class="h3 fw-bold text-dark d-block" id="metric-events">—</span>
                    </div>
                </div>
                <div class="organizer-metric">
                    <div class="panel-box h-100 mb-0 p-4">
                        <span class="filter-label d-block mb-1">Sự kiện chờ
                            duyệt</span><span class="h3 fw-bold text-dark d-block" id="metric-pending">—</span>
                    </div>
                </div>
                <div class="organizer-metric">
                    <div class="panel-box h-100 mb-0 p-4">
                        <span class="filter-label d-block mb-1">Đơn đã thanh
                            toán</span><span class="h3 fw-bold text-dark d-block" id="metric-orders">—</span>
                    </div>
                </div>
                <div class="organizer-metric">
                    <div class="panel-box h-100 mb-0 p-4">
                        <span class="filter-label d-block mb-1">Doanh thu lũy kế</span><span
                            class="h3 fw-bold text-danger d-block" id="metric-revenue">—</span>
                    </div>
                </div>
                <div class="organizer-metric">
                    <div class="panel-box h-100 mb-0 p-4">
                        <span class="filter-label d-block mb-1">Dòng tiền ròng lũy kế</span><span
                            class="h3 fw-bold text-dark d-block" id="metric-cash">—</span>
                    </div>
                </div>
            </div>
            <div class="panel-box mb-4">
                <div class="panel-header">
                    <span class="panel-title">7 ngày gần nhất</span>
                </div>
                <div id="org-chart" class="tc-chart" aria-label="Doanh thu 7 ngày"></div>
                <details>
                    <summary class="small mb-3">Xem số liệu chi tiết</summary>
                    <div class="table-responsive">
                        <table class="table-custom">
                            <thead>
                                <tr>
                                    <th>Ngày (UTC)</th>
                                    <th>Doanh thu theo đơn</th>
                                    <th>Dòng tiền ròng</th>
                                </tr>
                            </thead>
                            <tbody id="daily"></tbody>
                        </table>
                    </div>
                </details>
            </div>
            <div class="panel-box">
                <div class="panel-header">
                    <span class="panel-title">Sự kiện của tổ chức</span>
                    <a id="all-events" hidden class="small text-danger fw-bold text-decoration-none">Xem tất cả
                        sự kiện &rarr;</a>
                </div>
                <div class="table-responsive">
                    <table class="table-custom">
                        <thead>
                            <tr>
                                <th>Tên sự kiện</th>
                                <th>Bắt đầu</th>
                                <th>Sức chứa</th>
                                <th>Trạng thái</th>
                                <th>Thao tác</th>
                            </tr>
                        </thead>
                        <tbody id="events"></tbody>
                    </table>
                </div>
            </div>
            <noscript>Vui lòng bật JavaScript để xem số liệu.</noscript>
        </main>
    </div>
    <script defer src="${pageContext.request.contextPath}/assets/js/organizer/organizer-pages.js"></script>

</body>

</html>
