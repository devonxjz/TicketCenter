<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vé Của Tôi - TicketsCenter</title>

<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />

<main class="container py-5 buyer-container">
    <nav class="buyer-navigation" aria-label="Tài khoản"><a href="${pageContext.request.contextPath}/profile">Hồ sơ</a><a href="${pageContext.request.contextPath}/me/orders">Đơn hàng của tôi</a><a href="${pageContext.request.contextPath}/me/tickets" aria-current="page">Vé của tôi</a><a href="${pageContext.request.contextPath}/my-refunds">Hoàn vé</a></nav>
    <div class="buyer-heading">
        <h1 class="fw-bold mb-1">Vé của tôi</h1>
        <p class="text-muted mb-0">Quản lý các vé sự kiện bạn đã mua và xuất trình mã QR khi check-in</p>
    </div>

    <c:choose>
        <c:when test="${empty tickets}">
            <div class="empty-state">
                <i class="fa-regular fa-folder-open text-muted" style="font-size: 64px;"></i>
                <h4 class="fw-bold mt-3">Bạn chưa có vé nào</h4>
                <p class="text-muted">Các vé bạn mua thành công sẽ xuất hiện tại đây.</p>
                <a href="${pageContext.request.contextPath}/" class="btn btn-primary px-4 py-2 mt-2">
                    <i class="fa-solid fa-compass me-1"></i> Khám phá sự kiện ngay
                </a>
            </div>
        </c:when>
        <c:otherwise>
            <div class="row g-4" data-tc-paginate-items data-page-size="6">
                <c:forEach var="t" items="${tickets}">
                    <div class="col-md-6 col-lg-4">
                        <div class="ticket-card p-4 h-100 d-flex flex-column justify-content-between">
                            <div>
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <span class="text-muted small">${fn:escapeXml(t.zoneName)}</span>
                                    <c:choose>
                                        <c:when test="${t.status == 'ACTIVE'}">
                                            <span class="badge bg-success-subtle text-success badge-status">Đang hiệu lực</span>
                                        </c:when>
                                        <c:when test="${t.status == 'USED'}">
                                            <span class="badge bg-secondary-subtle text-secondary badge-status">Đã sử dụng</span>
                                        </c:when>
                                        <c:when test="${t.status == 'REFUND_PENDING'}">
                                            <span class="badge bg-warning-subtle text-warning badge-status">Chờ hoàn tiền</span>
                                        </c:when>
                                        <c:when test="${t.status == 'REFUNDED'}">
                                            <span class="badge bg-danger-subtle text-danger badge-status">Đã hoàn tiền</span>
                                        </c:when>
                                        <c:otherwise>
                                            <span class="badge bg-light text-dark badge-status">${fn:escapeXml(t.status)}</span>
                                        </c:otherwise>
                                    </c:choose>
                                </div>

                                <h5 class="fw-bold mb-2 text-truncate" title="${fn:escapeXml(t.eventTitle)}">${fn:escapeXml(t.eventTitle)}</h5>

                                <c:if test="${t.status == 'ACTIVE'}">
                                    <a href="${pageContext.request.contextPath}/tickets/${fn:escapeXml(t.id)}" class="d-block text-center mb-3" title="Mở vé điện tử">
                                        <img src="${pageContext.request.contextPath}/api/tickets/${fn:escapeXml(t.id)}/qr" alt="Mã QR vé ${fn:escapeXml(t.ticketCode)}" width="160" height="160" loading="lazy" class="border rounded bg-white p-1">
                                        <span class="d-block small font-monospace text-muted mt-1">${fn:escapeXml(t.ticketCode)}</span>
                                    </a>
                                </c:if>

                                <div class="text-muted small mb-1">
                                    <i class="fa-regular fa-clock me-1"></i>
                                    <span><time class="tc-local-time" datetime="${fn:escapeXml(t.startTime)}">${fn:escapeXml(t.startTime)}</time></span>
                                </div>

                                <div class="text-muted small mb-3 text-truncate" title="${fn:escapeXml(t.venueName)}">
                                    <i class="fa-solid fa-location-dot me-1"></i>
                                    <span>${fn:escapeXml(t.venueName)}</span>
                                </div>

                                <div class="bg-light p-3 rounded mb-3">
                                    <div class="d-flex justify-content-between small text-muted">
                                        <span>Khu vực:</span>
                                        <strong class="text-dark">${fn:escapeXml(not empty t.zoneName ? t.zoneName : 'Tiêu chuẩn')}</strong>
                                    </div>
                                    <div class="d-flex justify-content-between small text-muted mt-1">
                                        <span>Vị trí:</span>
                                        <strong class="text-dark">${fn:escapeXml(not empty t.seatLabel ? t.seatLabel : 'Vé tự do')}</strong>
                                    </div>
                                </div>
                            </div>

                            <div class="d-flex justify-content-between align-items-center mt-3 pt-3 border-top">
                                <div>
                                    <span class="small text-muted d-block">Giá vé</span>
                                    <span class="fw-bold text-primary fs-5">
                                        <fmt:formatNumber value="${t.paidAmount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                                    </span>
                                </div>
                                <a href="${pageContext.request.contextPath}/tickets/${fn:escapeXml(t.id)}" class="btn btn-qr px-3 py-2">
                                    <i class="fa-solid fa-qrcode me-1"></i> Chi tiết & QR
                                </a>
                            </div>
                        </div>
                    </div>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
</main>


<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>
</html>
