<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<% response.setHeader("Cache-Control", "no-store"); %>
<!DOCTYPE html>
<html lang="vi"><head><title>Thanh toán đơn vé | TicketsCenter</title>
<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ticket-hold.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page checkout-page" data-hold-page="current" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5 hold-page buyer-container">
<div class="buyer-heading"><a href="${pageContext.request.contextPath}/">← Khám phá sự kiện</a><h1 class="mt-3">Thanh toán đơn vé</h1><p class="text-muted mb-0">Kiểm tra vé đang giữ, chọn ưu đãi và hoàn tất đơn hàng.</p></div>
<p id="hold-message" role="status" aria-live="polite">Đang tải vé đang giữ…</p>
<div id="hold-auth" hidden><a href="${pageContext.request.contextPath}/auth/login" class="btn btn-dark">Đăng nhập</a> <a href="${pageContext.request.contextPath}/auth/verify-email">Xác minh email</a></div>
<div id="hold-recovery" hidden class="hold-notice"><p>Chưa xác định được kết quả yêu cầu. Thử lại để kiểm tra trạng thái.</p><button id="hold-retry" type="button" class="btn btn-dark">Thử lại yêu cầu</button></div>
<div class="checkout-grid">
<section id="current-hold" hidden class="hold-panel" aria-labelledby="held-tickets-heading">
<div class="hold-timer-bar mb-4"><span>Thời gian giữ vé còn lại</span><strong id="hold-countdown" role="timer" aria-label="Thời gian còn lại"></strong></div>
<h2 id="held-tickets-heading" class="h5 fw-bold">1. Vé đang giữ</h2><p id="hold-heading" class="checkout-event-title"></p><p id="hold-id" hidden class="hold-id text-muted small"></p><p id="hold-deadline" class="small text-muted"></p><ul id="hold-items" class="hold-items"></ul><p class="d-flex justify-content-between border-top pt-3">Giá vé đã giữ <strong id="hold-total"></strong></p>
<p class="small text-muted">Vé được giữ cho bạn đến hết thời gian trên. Bạn có thể thanh toán ngay hoặc quay lại thanh toán trước khi hết hạn.</p><div class="d-flex flex-wrap gap-2"><a id="hold-view" class="btn btn-outline-dark">Xem sự kiện</a><button id="hold-cancel" type="button" class="btn btn-outline-danger">Hủy lượt giữ</button></div>
</section>
<section id="order-checkout" class="hold-panel checkout-payment" aria-labelledby="order-heading">
<h2 id="order-heading" class="h5 fw-bold">2. Chi tiết đơn vé</h2><p id="order-message" role="status" aria-live="polite">Đang tải đơn hàng…</p>
<div id="order-recovery" hidden class="hold-notice"><p>Chưa xác định được kết quả yêu cầu. Thử lại với cùng yêu cầu đã lưu.</p><button id="order-retry" type="button" class="btn btn-dark">Thử lại yêu cầu đơn hàng</button></div>
<button id="order-create" hidden type="button" class="btn btn-dark w-100">Xem đơn &amp; tiếp tục thanh toán</button>
<form id="order-coupon-form" class="checkout-coupon"><label for="order-coupon-code" class="filter-label">Mã giảm giá</label><select id="order-coupon-code" class="form-select" aria-describedby="order-coupon-help" disabled><option value="">Đang tải mã giảm giá…</option></select><p id="order-coupon-help" class="small text-muted" role="status" aria-live="polite">Đang đối chiếu ưu đãi cho vé của bạn…</p></form>
<p id="order-preview-result" role="status" aria-live="polite" class="small"></p><p id="order-applied-coupon" class="small"></p>
<div id="order-summary" hidden><p id="order-code" class="hold-id small" hidden></p><p id="order-status" class="small text-muted"></p><ul id="order-items" class="hold-items" hidden></ul>
<dl class="checkout-total"><dt class="fw-normal text-muted">Tiền vé</dt><dd id="order-subtotal"></dd><dt class="fw-normal text-muted">Giảm giá</dt><dd id="order-discount"></dd><dt class="fw-bold">Tổng thanh toán</dt><dd id="order-total" class="h4"></dd></dl>
<div class="border-top pt-3"><h3 class="h6 fw-bold">Phương thức thanh toán</h3><p class="small text-muted">VNPAY · Đơn miễn phí được hoàn tất trực tiếp.</p><button id="order-pay" type="button" class="btn btn-primary w-100" disabled>Tiếp tục thanh toán</button><p id="payment-message" role="status" aria-live="polite" class="small mt-2"></p><a id="order-detail-link" class="small text-decoration-underline" hidden>Xem chi tiết đơn hàng</a></div>
</div>
<button id="order-refresh" type="button" class="btn btn-outline-dark mt-3">Cập nhật đơn hàng</button>
</section></div>
<button id="hold-refresh" type="button" class="btn btn-outline-dark mt-2">Cập nhật vé đang giữ</button>
<noscript>Vui lòng bật JavaScript để xem và thanh toán vé đang giữ.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script src="${pageContext.request.contextPath}/assets/js/ticket-hold.js?v=seat-coupon-9" defer></script>
<script src="${pageContext.request.contextPath}/assets/js/order-checkout.js?v=coupon-select-7" defer></script>
</body></html>
