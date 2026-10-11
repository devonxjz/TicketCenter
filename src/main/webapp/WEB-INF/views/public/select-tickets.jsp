<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Chọn vé | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ticket-hold.css">
</head>
<body data-hold-page="selection" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5 hold-page">
<a href="${pageContext.request.contextPath}/">Tất cả sự kiện</a><h1 id="hold-heading" class="h2 mt-4">Chọn vé</h1>
<p id="hold-message" role="status" aria-live="polite">Đang đối chiếu lượt giữ...</p>
<div id="hold-auth" hidden><a href="${pageContext.request.contextPath}/auth/login" class="btn btn-dark">Đăng nhập</a> <a href="${pageContext.request.contextPath}/auth/verify-email">Xác minh email</a></div>
<div id="hold-recovery" hidden class="hold-notice"><p>Kết quả yêu cầu chưa rõ. Thử lại cùng yêu cầu để đối chiếu với máy chủ.</p><button id="hold-retry" type="button" class="btn btn-dark">Thử lại yêu cầu</button></div>
<section id="current-hold" hidden class="hold-panel"><h2 class="h4">Lượt giữ hiện tại</h2><p id="hold-id" class="hold-id"></p><p>Thời gian còn lại <strong id="hold-countdown" role="timer" aria-label="Thời gian còn lại"></strong></p><p id="hold-deadline"></p><ul id="hold-items" class="hold-items"></ul><p>Giá vé đã giữ <strong id="hold-total"></strong></p><a id="hold-view" href="${pageContext.request.contextPath}/checkout" class="btn btn-dark">Xem lượt giữ</a> <button id="hold-cancel" type="button" class="btn btn-outline-danger">Hủy lượt giữ</button></section>
<form id="hold-form" hidden>
<p id="sale-deadline"></p><p>Chọn tối đa 8 vé trong cùng sự kiện. Chỗ chỉ được giữ khi máy chủ xác nhận.</p>
<fieldset id="hold-zones"><legend class="visually-hidden">Khu vé và ghế còn lại</legend></fieldset>
<div class="hold-summary"><p id="selection-count" aria-live="polite">Đã chọn 0 / 8 vé</p><p>Giá vé dự kiến <strong id="selection-total">0 đ</strong></p><button id="hold-submit" type="submit" class="btn btn-danger" disabled>Giữ vé trong 10 phút</button></div>
</form><button id="hold-refresh" type="button" class="btn btn-outline-dark mt-4">Cập nhật trạng thái</button>
<noscript>Vui lòng bật JavaScript để xem lượt giữ hiện tại.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script src="${pageContext.request.contextPath}/assets/js/ticket-hold.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
