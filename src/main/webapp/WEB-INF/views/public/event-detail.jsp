<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Chi tiết sự kiện | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
</head>
<body data-event-page="detail" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5">
<nav aria-label="breadcrumb" class="mb-4"><a href="${pageContext.request.contextPath}/" class="text-dark">Trang chủ</a> / Chi tiết sự kiện</nav>
<p id="event-message" role="status" aria-live="polite">Đang tải...</p><article id="event-detail" hidden><div class="row g-4 mb-5"><div id="event-cover" class="col-lg-5"></div>
<div class="col-lg-7"><h1 id="event-title" class="h2 fw-bold mb-4"></h1><dl class="row">
<dt class="col-sm-4">Bắt đầu</dt><dd id="event-start" class="col-sm-8"></dd>
<dt class="col-sm-4">Kết thúc</dt><dd id="event-end" class="col-sm-8"></dd>
<dt class="col-sm-4">Địa điểm</dt><dd id="event-venue" class="col-sm-8"></dd>
<dt class="col-sm-4">Địa chỉ</dt><dd id="event-address" class="col-sm-8"></dd>
<dt class="col-sm-4">Mở bán</dt><dd id="event-sale" class="col-sm-8"></dd>
</dl></div></div><section class="panel-box bg-white"><h2 class="h5 fw-bold">Giới thiệu chương trình</h2><p id="event-description" style="white-space:pre-wrap"></p></section></article>
<noscript>Vui lòng bật JavaScript để xem chi tiết sự kiện.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script src="${pageContext.request.contextPath}/assets/js/event-images.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
