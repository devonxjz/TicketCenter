<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Lịch Sử Đơn Hàng - TicketsCenter</title>

<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />

<main class="container py-5 buyer-container">
    <nav class="buyer-navigation" aria-label="Tài khoản"><a href="${pageContext.request.contextPath}/profile">Hồ sơ</a><a href="${pageContext.request.contextPath}/me/orders" aria-current="page">Đơn hàng của tôi</a><a href="${pageContext.request.contextPath}/me/tickets">Vé của tôi</a><a href="${pageContext.request.contextPath}/my-refunds">Hoàn vé</a></nav>
    <div class="d-flex justify-content-between align-items-center buyer-heading">
        <div>
            <h1 class="fw-bold mb-1">Đơn hàng của tôi</h1>
            <p class="text-muted mb-0">Xem và quản lý tất cả các đơn hàng mua vé sự kiện của bạn</p>
        </div>
        <div>
            <a href="${pageContext.request.contextPath}/me/tickets" class="btn btn-outline-primary me-2">
                <i class="fa-solid fa-ticket me-1"></i> Vé của tôi
            </a>
            <a href="${pageContext.request.contextPath}/" class="btn btn-outline-secondary">
                <i class="fa-solid fa-house me-1"></i> Trang chủ
            </a>
        </div>
    </div>

    <nav class="buyer-navigation buyer-filter" aria-label="Lọc đơn hàng">
        <a href="${pageContext.request.contextPath}/me/orders" ${empty currentStatus ? 'aria-current="page"' : ''}>Tất cả</a>
        <a href="${pageContext.request.contextPath}/me/orders?status=PAID" ${currentStatus == 'PAID' ? 'aria-current="page"' : ''}>Đã thanh toán</a>
        <a href="${pageContext.request.contextPath}/me/orders?status=PENDING_PAYMENT" ${currentStatus == 'PENDING_PAYMENT' ? 'aria-current="page"' : ''}>Chờ thanh toán</a>
        <a href="${pageContext.request.contextPath}/me/orders?status=CANCELLED" ${currentStatus == 'CANCELLED' ? 'aria-current="page"' : ''}>Đã hủy</a>
        <a href="${pageContext.request.contextPath}/me/orders?status=EXPIRED" ${currentStatus == 'EXPIRED' ? 'aria-current="page"' : ''}>Hết hạn</a>
    </nav>
    <p class="small text-muted">${historyPage.total} đơn hàng</p>
    <c:choose>
        <c:when test="${empty historyPage.orders}">
            <div class="text-center py-5 bg-white rounded-4 border border-dashed p-5">
                <i class="fa-solid fa-cart-shopping text-muted mb-3" style="font-size: 56px;"></i>
                <h4 class="fw-bold">Bạn chưa có đơn hàng nào</h4>
                <p class="text-muted">Sau khi đặt vé thành công, đơn hàng của bạn sẽ hiển thị tại đây.</p>
                <a href="${pageContext.request.contextPath}/" class="btn btn-primary px-4 py-2 mt-2">
                    <i class="fa-solid fa-compass me-1"></i> Khám phá sự kiện
                </a>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row g-3">
                <c:forEach var="o" items="${historyPage.orders}">
                    <div class="col-12">
                        <div class="order-card p-4">
                            <div class="d-flex flex-wrap justify-content-between align-items-center mb-3 pb-3 border-bottom">
                                <div class="d-flex align-items-center gap-3">
                                    <span class="badge bg-light text-dark font-monospace border px-2 py-1">Mã đơn: ${fn:escapeXml(o.orderCode)}</span>
                                    <span class="text-muted small"><i class="fa-regular fa-clock me-1"></i>${fn:escapeXml(o.createdAt)}</span>
                                </div>
                                <div>
                                    <c:choose>
                                        <c:when test="${o.status == 'PAID'}">
                                            <span class="badge bg-success-subtle text-success badge-status">Đã thanh toán</span>
                                        </c:when>
                                        <c:when test="${o.status == 'PENDING_PAYMENT'}">
                                            <span class="badge bg-warning-subtle text-warning badge-status">Chờ thanh toán</span>
                                        </c:when>
                                        <c:when test="${o.status == 'CANCELLED'}">
                                            <span class="badge bg-secondary-subtle text-secondary badge-status">Đã hủy</span>
                                        </c:when>
                                        <c:when test="${o.status == 'EXPIRED'}">
                                            <span class="badge bg-danger-subtle text-danger badge-status">Hết hạn</span>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="badge bg-light text-dark badge-status">${fn:escapeXml(o.status)}</span>
                                        </c:otherwise>
                                    </c:choose>
                                </div>
                            </div>

                            <div class="row align-items-center">
                                <div class="col-md-7 mb-3 mb-md-0">
                                    <h5 class="fw-bold mb-1">${fn:escapeXml(o.eventTitle)}</h5>
                                    <p class="text-muted small mb-2"><i class="fa-solid fa-location-dot me-1"></i>${fn:escapeXml(o.venueName)}</p>
                                    <span class="badge bg-primary-subtle text-primary">
                                        <i class="fa-solid fa-ticket me-1"></i>${fn:escapeXml(o.ticketCount)} vé
                                    </span>
                                    <c:if test="${not empty o.couponCode}">
                                        <span class="badge bg-info-subtle text-info ms-2">
                                            <i class="fa-solid fa-tag me-1"></i>Mã giảm giá: ${fn:escapeXml(o.couponCode)}
                                        </span>
                                    </c:if>
                                </div>
                                <div class="col-md-5 text-md-end">
                                    <div class="mb-2">
                                        <span class="text-muted small d-block">Tổng số tiền</span>
                                        <span class="fs-4 fw-bold text-primary">
                                            <fmt:formatNumber value="${o.totalAmount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                                        </span>
                                    </div>
                                    <a href="${pageContext.request.contextPath}/orders/${fn:escapeXml(o.id)}" class="btn btn-outline-primary btn-sm px-3">
                                        <i class="fa-solid fa-circle-info me-1"></i> Chi tiết đơn hàng
                                    </a>
                                </div>
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
    <nav class="d-flex justify-content-between align-items-center mt-4" aria-label="Phân trang đơn hàng">
        <c:if test="${historyPage.page > 1}"><c:url var="previousPage" value="/me/orders"><c:param name="page" value="${historyPage.page - 1}"/><c:param name="size" value="${historyPage.size}"/><c:if test="${not empty currentStatus}"><c:param name="status" value="${currentStatus}"/></c:if></c:url><a class="btn btn-outline-dark" href="${fn:escapeXml(previousPage)}">← Trang trước</a></c:if>
        <span class="small text-muted">Trang ${historyPage.page}</span>
        <c:if test="${historyPage.page * historyPage.size < historyPage.total}"><c:url var="nextPage" value="/me/orders"><c:param name="page" value="${historyPage.page + 1}"/><c:param name="size" value="${historyPage.size}"/><c:if test="${not empty currentStatus}"><c:param name="status" value="${currentStatus}"/></c:if></c:url><a class="btn btn-outline-dark" href="${fn:escapeXml(nextPage)}">Trang sau →</a></c:if>
    </nav>
</main>


<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>
</html>
