<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Hủy sự kiện | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/organizer-ui.css?v=20261014">
    <style>
        [hidden] { display: none !important; }
        .tc-cancel-actions { display: flex; gap: 12px; flex-wrap: wrap; }
        .tc-cancel-stat { border-top: 1px solid #ddd; padding: 18px 0; }
        .tc-cancel-warning { border-left: 3px solid #bf2026; padding: 12px 18px; background: #fff; }
        .tc-cancel-reason { width: 100%; min-height: 120px; border: 1px solid #bbb; padding: 12px; }
        progress { width: 100%; accent-color: #bf2026; }
    </style>
</head>
<body class="tc-organizer-page" data-context="${pageContext.request.contextPath}" data-cancellation-admin="${cancellationAdmin}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<div class="dashboard-layout">
    <c:choose>
        <c:when test="${cancellationAdmin}">
            <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp" />
        </c:when>
        <c:otherwise>
            <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
        </c:otherwise>
    </c:choose>
    <main class="dashboard-content">
        <a class="text-dark" href="${pageContext.request.contextPath}/events/${cancellationAdmin ? 'review' : 'manage'}">← Danh sách sự kiện</a>
        <h1 class="h3 fw-bold mt-4">Yêu cầu hủy sự kiện</h1>
        <p class="text-muted">Yêu cầu được quản trị viên xem xét trước khi hủy chương trình.</p>
        <div class="panel-box">
            <form id="cancellation-select" class="mb-4">
                <div id="cancellation-organization-field" hidden>
                    <label for="cancellation-organization" class="filter-label">Tổ chức</label>
                    <select id="cancellation-organization" class="form-select mb-3"></select>
                </div>
                <label for="cancellation-event-id" class="filter-label">Chọn sự kiện</label>
                <div class="tc-cancel-actions">
                    <select id="cancellation-event-id" class="form-select" style="max-width:520px" required>
                        <option value="">Đang tải danh sách…</option>
                    </select>
                    <button class="btn btn-dark">Xem yêu cầu</button>
                </div>
            </form>
            <nav id="cancellation-picker-paging" class="tc-cancel-actions mb-3" aria-label="Phân trang danh sách sự kiện">
                <button type="button" id="cancellation-picker-previous" class="btn btn-outline-dark" disabled>Trang trước</button>
                <span id="cancellation-picker-page"></span>
                <button type="button" id="cancellation-picker-next" class="btn btn-outline-dark" disabled>Trang sau</button>
            </nav>
            <p id="cancellation-status" role="status" aria-live="polite">Chọn sự kiện để xem trạng thái hiện tại.</p>
            <section id="cancellation-detail" hidden>
                <h2 class="h4" id="cancellation-title"></h2>
                <p id="cancellation-state"></p>
                <p id="cancellation-request-reason" class="tc-cancel-warning" hidden></p>
                <p id="cancellation-rejection" hidden></p>
                <form id="cancellation-action-form">
                    <label for="cancellation-reason" class="filter-label">Lý do yêu cầu hoặc từ chối</label>
                    <textarea id="cancellation-reason" class="tc-cancel-reason" minlength="10" maxlength="500" placeholder="Nhập từ 10 đến 500 ký tự"></textarea>
                    <div class="tc-cancel-actions mt-3">
                        <button type="button" id="cancellation-request" class="btn btn-dark" hidden>Gửi yêu cầu hủy</button>
                        <button type="button" id="cancellation-approve" class="btn btn-danger" hidden>Duyệt và hủy sự kiện</button>
                        <button type="button" id="cancellation-reject" class="btn btn-outline-dark" hidden>Từ chối yêu cầu</button>
                        <button type="button" id="cancellation-retry" class="btn btn-dark" hidden>Kiểm tra lại lệnh đã gửi</button>
                    </div>
                </form>
                <section id="cancellation-progress-panel" class="tc-cancel-stat" hidden>
                    <h3 class="h5">Tiến trình chuẩn bị hoàn vé</h3>
                    <progress id="cancellation-progress-bar" max="100" value="0"></progress>
                    <p id="cancellation-progress-counts"></p>
                    <p id="cancellation-refund-counts"></p>
                    <p class="text-muted">Xử lý hết danh sách đơn không có nghĩa đã chuyển tiền. Kết quả hoàn chỉ được ghi nhận từ cơ sở dữ liệu; hoàn tiền đang được mô phỏng.</p>
                </section>
            </section>
        </div>
    </main>
</div>
<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script defer src="${pageContext.request.contextPath}/assets/js/event-cancellation-review.js"></script>
</body>
</html>
