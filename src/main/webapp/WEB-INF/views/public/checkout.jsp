<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<% response.setHeader("Cache-Control", "no-store"); %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Đơn hàng và mã giảm giá | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ticket-hold.css">
</head>
<body data-hold-page="current" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5 hold-page">
<a href="${pageContext.request.contextPath}/">Tất cả sự kiện</a><h1 id="hold-heading" class="h2 mt-4">Lượt giữ vé</h1>
<p id="hold-message" role="status" aria-live="polite">Đang đối chiếu lượt giữ...</p>
<div id="hold-auth" hidden><a href="${pageContext.request.contextPath}/auth/login" class="btn btn-dark">Đăng nhập</a> <a href="${pageContext.request.contextPath}/auth/verify-email">Xác minh email</a></div>
<div id="hold-recovery" hidden class="hold-notice"><p>Kết quả yêu cầu chưa rõ. Thử lại cùng yêu cầu để đối chiếu với máy chủ.</p><button id="hold-retry" type="button" class="btn btn-dark">Thử lại yêu cầu</button></div>
<section id="current-hold" hidden class="hold-panel"><h2 class="h4">Vé đang giữ</h2><p id="hold-id" class="hold-id"></p><p>Thời gian còn lại <strong id="hold-countdown" role="timer" aria-label="Thời gian còn lại"></strong></p><p id="hold-deadline"></p><ul id="hold-items" class="hold-items"></ul><p>Giá vé đã giữ <strong id="hold-total"></strong></p><p>Vé đang được giữ và chưa thanh toán.</p><a id="hold-view" class="btn btn-outline-dark">Xem sự kiện</a> <button id="hold-cancel" type="button" class="btn btn-outline-danger">Hủy lượt giữ</button></section>
<button id="hold-refresh" type="button" class="btn btn-outline-dark mt-4">Cập nhật trạng thái</button>
<section id="order-checkout" class="hold-panel" aria-labelledby="order-heading">
<h2 id="order-heading" class="h4">Đơn hàng</h2>
<p id="order-message" role="status" aria-live="polite">Đang đối chiếu đơn hàng...</p>
<div id="order-recovery" hidden class="hold-notice"><p>Yêu cầu chưa rõ kết quả. Thử lại cùng mã yêu cầu đã lưu.</p><button id="order-retry" type="button" class="btn btn-dark">Thử lại yêu cầu đơn hàng</button></div>
<button id="order-create" hidden type="button" class="btn btn-dark">Xác nhận tạo đơn từ vé đang giữ</button>
<div id="order-summary" hidden>
<p id="order-code" class="hold-id"></p><p id="order-status"></p>
<ul id="order-items" class="hold-items"></ul>
<dl><dt>Tiền vé</dt><dd id="order-subtotal"></dd><dt>Giảm giá</dt><dd id="order-discount"></dd><dt>Tổng cần thanh toán</dt><dd id="order-total" class="h4"></dd></dl>
<p id="order-applied-coupon"></p>
<form id="order-coupon-form"><label for="order-coupon-code" class="form-label">Mã giảm giá</label><input id="order-coupon-code" class="form-control" type="text" maxlength="80" autocomplete="off" aria-describedby="order-coupon-help"><p id="order-coupon-help" class="text-muted">Mã được gửi đúng như bạn nhập. Xem trước không giữ lượt sử dụng.</p><button id="order-preview" type="button" class="btn btn-outline-dark">Xem trước</button> <button id="order-apply" type="submit" class="btn btn-dark">Áp dụng mã</button> <button id="order-remove" type="button" class="btn btn-outline-danger">Gỡ mã</button></form>
<p id="order-preview-result" role="status" aria-live="polite"></p>
<p>Đơn chưa thanh toán. Thời hạn giữ vé không thay đổi khi tạo đơn hoặc áp dụng mã giảm giá.</p>
</div>
<button id="order-refresh" type="button" class="btn btn-outline-dark mt-3">Đối chiếu đơn hàng</button>
</section>
<noscript>Vui lòng bật JavaScript để xem lượt giữ hiện tại.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script src="${pageContext.request.contextPath}/assets/js/ticket-hold.js" defer></script>
<script src="${pageContext.request.contextPath}/assets/js/order-checkout.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body></html>
