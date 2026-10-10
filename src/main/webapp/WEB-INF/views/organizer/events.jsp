<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Sự kiện tổ chức | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
</head>
<body data-event-page="manager-list" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<div class="dashboard-layout"><jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" /><main class="dashboard-content">
<div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4"><h1 class="h3 fw-bold mb-0">Sự kiện tổ chức</h1>
<a id="event-create-link" href="${pageContext.request.contextPath}/organizer/events/new" class="btn-primary-custom">Tạo mới sự kiện</a></div>
<label for="event-organization" class="filter-label">Tổ chức</label><select id="event-organization" class="form-select rounded-0 mb-4" disabled></select>
<p id="event-message" role="status" aria-live="polite">Đang tải...</p>
<div class="panel-box">
    <div class="panel-header">
        <span class="panel-title">Tất cả chương trình</span>
        <label class="visually-hidden" for="event-search">Tìm kiếm sự kiện</label>
        <input id="event-search" type="search" class="form-control form-control-sm rounded-0" placeholder="Tìm kiếm sự kiện..." style="max-width:220px">
    </div>
    <div class="table-responsive">
        <table class="table-custom">
            <thead><tr><th>Ảnh bìa</th><th>Tên sự kiện</th><th>Thời gian diễn ra</th><th>Địa điểm</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
            <tbody id="event-list"></tbody>
        </table>
    </div>
</div>
<noscript>Vui lòng bật JavaScript để quản lý sự kiện.</noscript>
</main></div>
<script src="${pageContext.request.contextPath}/assets/js/event-images.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
