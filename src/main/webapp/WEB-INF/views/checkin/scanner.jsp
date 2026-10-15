<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Soát vé sự kiện · TicketsCenter</title>
<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" /><link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-ui.css">
</head><body class="checkin-body scanner-body"><a class="portal-skip" href="#main-content">Đến nội dung chính</a><header class="checkin-topbar"><a id="back" href="${pageContext.request.contextPath}/check-ins">← Đổi sự kiện</a><span class="portal-label">Cổng soát vé</span></header><main id="main-content">

<h1 id="event-title">Đang tải sự kiện…</h1><p id="window" class="muted"></p><p id="notice" role="alert"></p>
<div class="grid"><section class="panel"><h2>Quét mã QR</h2><div id="reader"></div>
<button id="camera" type="button" disabled>Bật camera</button><button id="stop" type="button" class="secondary" disabled>Tắt camera</button>
<label for="image">Hoặc chọn ảnh QR trên thiết bị</label><input id="image" type="file" accept="image/*" disabled>
<p class="muted">Camera cần quyền truy cập và kết nối HTTPS. Ảnh QR được đọc trên thiết bị.</p>
<form id="manual"><label for="code">Nhập mã vé hoặc nội dung QR</label><textarea id="code" maxlength="4096" required autocomplete="off" spellcheck="false" disabled></textarea>
<button id="submit" type="submit" disabled>Kiểm tra vé</button></form></section>
<section id="result" class="panel" role="status" aria-live="polite"><h2>Sẵn sàng soát vé</h2><p>Quét QR hoặc nhập mã để kiểm tra.</p></section></div>
<button id="retry" type="button" hidden>Tra cứu lại kết quả</button><button id="next" type="button" class="secondary" hidden>Quét vé tiếp theo</button>
<section class="panel"><h2>Lượt soát vé gần đây</h2><div id="history" tabindex="0" aria-label="Nhật ký soát vé" aria-live="polite">Đang tải nhật ký…</div><button id="reload" type="button" class="secondary">Làm mới nhật ký</button></section>
<footer class="scanner-footer">TicketsCenter · Soát vé sự kiện · Thời gian theo Việt Nam</footer></main><script src="../assets/checkin/vendor/html5-qrcode.min.js?v=2.3.8"></script><script src="../assets/checkin/scanner.js?v=20261013"></script></body></html>
