<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Khám phá sự kiện | TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/event-images.css">
</head>
<body data-event-page="catalog" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5">
<div id="heroCarousel" class="carousel slide hero-banner mt-2 mb-4" data-bs-ride="carousel" hidden>
    <div id="event-highlights" class="carousel-inner"></div>
    <button class="carousel-control-prev" type="button" data-bs-target="#heroCarousel" data-bs-slide="prev" aria-label="Sự kiện trước"><span class="carousel-control-prev-icon" aria-hidden="true"></span></button>
    <button class="carousel-control-next" type="button" data-bs-target="#heroCarousel" data-bs-slide="next" aria-label="Sự kiện tiếp theo"><span class="carousel-control-next-icon" aria-hidden="true"></span></button>
</div>
<div class="d-flex justify-content-between align-items-center mb-4"><h2 class="section-title mb-0">Sự kiện đang công bố</h2><a href="${pageContext.request.contextPath}/organizer/events" class="small text-dark">Quản lý sự kiện</a></div>
<p id="event-message" role="status" aria-live="polite">Đang tải...</p><div id="event-list" class="row g-4"></div>
<noscript>Vui lòng bật JavaScript để xem sự kiện.</noscript>
</main><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script src="${pageContext.request.contextPath}/assets/js/event-images.js" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
