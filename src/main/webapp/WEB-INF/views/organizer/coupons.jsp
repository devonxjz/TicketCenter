<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <% response.setHeader("Cache-Control", "no-store" ); %>
        <!DOCTYPE html>
        <html lang="vi">

        <head>
            <title>Mã giảm giá | TicketsCenter</title>
            <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
            <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css">
            <style>
                [hidden] {
                    display: none !important
                }
            </style>
        </head>

        <body class="bg-light tc-organizer-page" data-live-page="organizer-coupons"
            data-context="${pageContext.request.contextPath}">
            <jsp:include page="/WEB-INF/views/layout/header.jsp" />
            <div class="dashboard-layout">
                <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
                <main class="dashboard-content">
                    <div class="d-flex flex-column flex-sm-row justify-content-between align-items-sm-end gap-3 mb-5">
                        <div>
                            <h1 class="h3 fw-bold mb-1 text-dark">Quản lý mã khuyến mãi</h1>
                            <p class="small text-muted mb-0">Tạo mã giảm giá và theo dõi số lượt sử dụng thực tế.</p>
                        </div>
                        <div><label class="form-label small mb-1" for="organization">Tổ chức</label><select
                                id="organization" class="form-select form-select-sm rounded-0"></select></div>
                    </div>
                    <p id="live-status" role="status" aria-live="polite" class="text-muted">Đang tải mã giảm giá…</p>
                    <div class="p-3 bg-white border small text-muted mb-4"><strong>Chính sách chiết khấu:</strong> Mã
                        giảm giá áp dụng tối đa 30% giá trị đơn hàng.</div>
                    <details class="organizer-coupon-create mb-4">
                        <summary class="btn-primary-custom">Tạo mã khuyến mãi mới</summary>
                        <div class="panel-box">
                            <div class="panel-header"><span class="panel-title">Tạo mã mới</span></div>
                            <form id="coupon-form" class="row g-3">
                                <div class="col-md-4"><label class="filter-label" for="coupon-code">Mã</label><input
                                        id="coupon-code" class="form-control rounded-0" maxlength="80" required
                                        autocomplete="off"></div>
                                <div class="col-md-4"><label class="filter-label" for="coupon-type">Loại
                                        giảm</label><select id="coupon-type" class="form-select rounded-0">
                                        <option value="PERCENTAGE">Phần trăm</option>
                                        <option value="FIXED_AMOUNT">Số tiền cố định</option>
                                    </select></div>
                                <div class="col-md-4" id="coupon-percent-group"><label class="filter-label"
                                        for="coupon-percent">Phần trăm (%)</label><input id="coupon-percent"
                                        type="number" class="form-control rounded-0" min="1" max="30" step="0.01"></div>
                                <div class="col-md-4" id="coupon-fixed-group" hidden><label class="filter-label"
                                        for="coupon-fixed">Số tiền (đ)</label><input id="coupon-fixed" type="number"
                                        class="form-control rounded-0" min="1" step="1"></div>
                                <div class="col-md-4"><label class="filter-label" for="coupon-max-uses">Số lượt tối
                                        đa</label><input id="coupon-max-uses" type="number"
                                        class="form-control rounded-0" min="1" step="1" required></div>
                                <div class="col-md-4"><label class="filter-label" for="coupon-from">Hiệu lực
                                        từ</label><input id="coupon-from" type="datetime-local"
                                        class="form-control rounded-0" required></div>
                                <div class="col-md-4"><label class="filter-label" for="coupon-to">Hiệu lực
                                        đến</label><input id="coupon-to" type="datetime-local"
                                        class="form-control rounded-0" required></div>
                                <div class="col-12"><button class="btn btn-dark rounded-0">Tạo mã</button></div>
                            </form>
                        </div>
                    </details>
                    <div class="panel-box mb-4">
                        <div class="panel-header"><span class="panel-title">Danh sách mã</span></div>
                        <div class="table-responsive">
                            <table class="table-custom">
                                <thead>
                                    <tr>
                                        <th>Mã</th>
                                        <th>Giá trị</th>
                                        <th>Đã dùng / Tổng</th>
                                        <th>Còn lại</th>
                                        <th>Hiệu lực</th>
                                        <th>Trạng thái</th>
                                        <th>Thao tác</th>
                                    </tr>
                                </thead>
                                <tbody id="coupons"></tbody>
                            </table>
                        </div>
                    </div>
                    <noscript>Vui lòng bật JavaScript để quản lý mã giảm giá.</noscript>
                </main>
            </div>
            <script defer src="${pageContext.request.contextPath}/assets/js/live-pages.js"></script>

        </body>

        </html>