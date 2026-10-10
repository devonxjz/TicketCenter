<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Biên tập sự kiện | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
</head>
<body data-event-page="editor" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<div class="dashboard-layout"><jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" /><main class="dashboard-content">
<div class="d-flex flex-wrap justify-content-between align-items-center gap-3 mb-4"><h1 id="editor-heading" class="h3 fw-bold mb-0">Tạo mới sự kiện</h1>
<a href="${pageContext.request.contextPath}/organizer/events" class="btn-outline-custom">Danh sách sự kiện</a></div>
<p id="event-message" role="status" aria-live="polite">Đang tải...</p><form id="event-form" enctype="multipart/form-data" method="post" hidden><fieldset id="event-fields" disabled>
<div class="panel-box bg-white mb-4"><div class="row g-4">
<div class="col-md-6"><label for="event-organization" class="filter-label">Tổ chức *</label><select id="event-organization" name="organizationId" class="form-select rounded-0" required></select></div>
<div class="col-md-6"><label for="categoryId" class="filter-label">Thể loại *</label><select id="categoryId" name="categoryId" class="form-select rounded-0" required></select></div>
<div class="col-12"><label for="title" class="filter-label">Tên chương trình *</label><input id="title" name="title" type="text" maxlength="250" class="form-control rounded-0" required></div>
<div class="col-md-6"><label for="venueName" class="filter-label">Tên địa điểm *</label><input id="venueName" name="venueName" type="text" maxlength="250" class="form-control rounded-0" required></div>
<div class="col-12"><label for="venueAddress" class="filter-label">Địa chỉ *</label><input id="venueAddress" name="venueAddress" type="text" maxlength="500" class="form-control rounded-0" required></div>
<div class="col-md-6"><label for="saleStart" class="filter-label">Bắt đầu mở bán *</label><input id="saleStart" name="saleStart" type="datetime-local" step="0.001" class="form-control rounded-0" required></div>
<div class="col-md-6"><label for="saleEnd" class="filter-label">Kết thúc mở bán *</label><input id="saleEnd" name="saleEnd" type="datetime-local" step="0.001" class="form-control rounded-0" required></div>
<div class="col-md-6"><label for="startTime" class="filter-label">Bắt đầu sự kiện *</label><input id="startTime" name="startTime" type="datetime-local" step="0.001" class="form-control rounded-0" required></div>
<div class="col-md-6"><label for="endTime" class="filter-label">Kết thúc sự kiện *</label><input id="endTime" name="endTime" type="datetime-local" step="0.001" class="form-control rounded-0" required></div>
<div class="col-12"><label for="description" class="filter-label">Giới thiệu chương trình *</label><textarea id="description" name="description" class="form-control rounded-0" rows="5" maxlength="5000" required></textarea></div>
</div></div><div class="panel-box bg-white mb-4"><h2 class="h5 fw-bold">Ảnh bìa sự kiện</h2>
<div id="event-cover-preview" class="mb-3"></div><label for="event-file" class="filter-label">Chọn ảnh PNG hoặc JPEG</label>
<input id="event-file" name="file" type="file" accept="image/png,image/jpeg" class="form-control rounded-0" aria-describedby="event-file-help">
<p id="event-file-help" class="small text-muted mt-2 mb-0">Tối đa 5 MiB. Không chọn ảnh mới để giữ ảnh hiện tại. Sự kiện mới không có ảnh dùng ảnh mặc định theo thể loại.</p></div>
<p class="small text-muted">Lịch bán vé phải thỏa mãn mở bán &lt; đóng bán &le; bắt đầu &lt; kết thúc. Sửa thông tin ở trạng thái bản nháp hoặc bị từ chối. Sự kiện đã công bố cho phép thay ảnh bìa.</p>
<div class="d-flex gap-3"><button id="event-save" type="submit" class="btn-primary-custom">Lưu bản nháp</button><a href="${pageContext.request.contextPath}/organizer/events" class="btn-outline-custom">Quay lại</a></div>
</fieldset></form><noscript>Vui lòng bật JavaScript để biên tập sự kiện.</noscript>
</main></div>
<script src="${pageContext.request.contextPath}/assets/js/event-images.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
