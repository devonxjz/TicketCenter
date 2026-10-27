<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%
    response.setHeader("Cache-Control", "no-store");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Thanh toán đơn vé | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/ticket-hold.css">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page checkout-page" data-hold-page="current" data-context="${pageContext.request.contextPath}">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <main class="container my-5 hold-page buyer-container">
        <div id="checkout-autopay-overlay" ${param.pay=='now' ? '' : 'hidden' } class="checkout-overlay">
            <div class="spinner-border text-danger" role="status" style="width: 3rem; height: 3rem;"></div>
            <h3 class="fw-bold mt-3 mb-1">Đang chuyển đến cổng thanh toán…</h3>
            <p id="checkout-autopay-text" class="text-muted mb-0">Vui lòng đợi trong giây lát, đang mở trang thanh toán
                VietQR payOS…</p>
        </div>
        <div class="buyer-heading mb-4">
            <a href="${pageContext.request.contextPath}/" class="text-decoration-none text-muted small">← Quay lại khám
                phá sự kiện</a>
            <h1 class="h3 fw-bold mt-2 mb-1">Xác nhận &amp; Thanh toán</h1>
            <p class="text-muted small mb-0">Kiểm tra thông tin vé, chọn ưu đãi và quét mã QR để hoàn tất đơn hàng.</p>
        </div>
        <p id="hold-message" role="status" aria-live="polite" class="text-muted small"></p>
        <div id="hold-auth" hidden class="alert alert-warning mb-3">
            <span class="me-2">Bạn cần đăng nhập để tiếp tục:</span>
            <a href="${pageContext.request.contextPath}/auth/login" class="btn btn-dark btn-sm me-2">Đăng nhập</a>
            <a href="${pageContext.request.contextPath}/auth/verify-email" class="btn btn-outline-secondary btn-sm">Xác
                minh email</a>
        </div>
        <div id="hold-recovery" hidden class="hold-notice alert alert-warning mb-3">
            <p class="small mb-2">Chưa xác định được kết quả yêu cầu. Thử lại để kiểm tra trạng thái.</p>
            <button id="hold-retry" type="button" class="btn btn-dark btn-sm">Thử lại yêu cầu</button>
        </div>
        <div class="checkout-grid">
            <section id="current-hold" hidden class="hold-panel" aria-labelledby="held-tickets-heading">
                <div class="hold-timer-bar mb-3">
                    <span>Thời gian giữ vé</span>
                    <strong id="hold-countdown" role="timer" aria-label="Thời gian còn lại">--:--</strong>
                </div>
                <h2 id="held-tickets-heading" class="h5 fw-bold mb-2">Thông tin vé đã chọn</h2>
                <p id="hold-heading" class="checkout-event-title fw-semibold fs-5 text-dark mb-1"></p>
                <p id="hold-id" hidden class="hold-id text-muted small"></p>
                <p id="hold-deadline" class="small text-muted mb-3"></p>
                <ul id="hold-items" class="hold-items mb-3"></ul>
                <div class="d-flex justify-content-between align-items-center border-top pt-3 mb-3">
                    <span class="text-muted">Tạm tính tiền vé</span>
                    <strong id="hold-total" class="fs-5 text-dark"></strong>
                </div>
                <div class="d-flex flex-wrap gap-2 pt-2 border-top">
                    <a id="hold-view" class="btn btn-outline-secondary btn-sm">Chọn lại vé</a>
                    <button id="hold-cancel" type="button" class="btn btn-outline-danger btn-sm">Hủy giữ vé</button>
                </div>
            </section>
            <section id="order-checkout" class="hold-panel checkout-payment" aria-labelledby="order-heading">
                <h2 id="order-heading" class="h5 fw-bold mb-3">Thanh toán</h2>
                <p id="order-message" role="status" aria-live="polite" class="small text-muted mb-2">Đang tải đơn hàng…
                </p>
                <div id="order-recovery" hidden class="hold-notice alert alert-warning small mb-3">
                    <p class="mb-2">Chưa xác định được kết quả yêu cầu. Thử lại với cùng yêu cầu đã lưu.</p>
                    <button id="order-retry" type="button" class="btn btn-dark btn-sm">Thử lại yêu cầu đơn hàng</button>
                </div>
                <button id="order-create" hidden type="button" class="btn-primary-custom w-100 mb-3">Tiến hành thanh
                    toán</button>
                <form id="order-coupon-form" class="checkout-coupon mb-3">
                    <label for="order-coupon-code" class="filter-label fw-semibold small mb-1">Mã giảm giá</label>
                    <select id="order-coupon-code" class="form-select" aria-describedby="order-coupon-help" disabled>
                        <option value="">Đang tải mã giảm giá…</option>
                    </select>
                    <p id="order-coupon-help" class="small text-muted mt-1 mb-0" role="status" aria-live="polite">Đang
                        đối chiếu ưu đãi cho vé của bạn…</p>
                </form>
                <p id="order-preview-result" role="status" aria-live="polite" class="small text-success mb-1"></p>
                <p id="order-applied-coupon" class="small fw-semibold text-primary mb-2"></p>
                <div id="order-summary" hidden>
                    <p id="order-code" class="hold-id small" hidden></p>
                    <p id="order-status" class="small text-muted mb-2"></p>
                    <ul id="order-items" class="hold-items" hidden></ul>
                    <dl class="checkout-total mb-3">
                        <dt class="fw-normal text-muted">Tiền vé</dt>
                        <dd id="order-subtotal" class="fw-medium"></dd>
                        <dt class="fw-normal text-muted">Giảm giá</dt>
                        <dd id="order-discount" class="text-success fw-medium"></dd>
                        <dt class="fw-bold">Tổng thanh toán</dt>
                        <dd id="order-total" class="h4 fw-bold text-danger mb-0"></dd>
                    </dl>
                    <div class="border rounded p-3 mb-3 bg-light">
                        <div class="d-flex align-items-center justify-content-between mb-1">
                            <span class="fw-semibold small">Hình thức thanh toán</span>
                            <span
                                class="badge bg-danger-subtle text-danger border border-danger-subtle fw-semibold">VietQR
                                / payOS</span>
                        </div>
                        <p class="small text-muted mb-0">Quét mã QR qua app ngân hàng hoặc ví điện tử để thanh toán tức
                            thì.</p>
                    </div>
                    <button id="order-pay" type="button" class="btn-primary-custom w-100 py-3 fw-bold fs-6"
                        disabled>Thanh toán ngay (payOS QR)</button>
                    <p id="payment-message" role="status" aria-live="polite"
                        class="small text-center text-muted mt-2 mb-0"></p>
                    <div class="text-center mt-2">
                        <a id="order-detail-link" class="small text-decoration-underline" hidden>Xem chi tiết đơn
                            hàng</a>
                    </div>
                </div>
                <button id="order-refresh" type="button" class="visually-hidden">Cập nhật đơn hàng</button>
            </section>
        </div>
        <button id="hold-refresh" type="button" class="visually-hidden">Cập nhật vé đang giữ</button>
        <noscript>Vui lòng bật JavaScript để xem và thanh toán vé đang giữ.</noscript>
    </main>
    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
    <script src="${pageContext.request.contextPath}/assets/js/order/ticket-selection.js?v=seat-coupon-10" defer></script>
    <script src="${pageContext.request.contextPath}/assets/js/order/checkout.js?v=coupon-select-11"
        defer></script>
</body>
</html>
