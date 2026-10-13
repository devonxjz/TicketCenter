<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vé điện tử - ${fn:escapeXml(ticket.eventTitle)} - TicketsCenter</title>

<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />

<main class="container">
    <div class="ticket-wrapper">
        <div class="mb-3 d-flex justify-content-between align-items-center">
            <a href="${pageContext.request.contextPath}/me/tickets" class="btn btn-outline-secondary btn-sm">
                <i class="fa-solid fa-arrow-left me-1"></i> Danh sách vé
            </a>
            <span class="badge bg-secondary font-monospace">${fn:escapeXml(ticket.ticketCode)}</span>
        </div>

        <div class="ticket-pass">
            <!-- Header -->
            <div class="ticket-header">
                <span class="badge bg-white text-primary mb-2 px-3 py-1 fw-bold rounded-pill">VÉ ĐIỆN TỬ</span>
                <h1 class="h4 fw-bold mb-1">${fn:escapeXml(ticket.eventTitle)}</h1>
                <p class="mb-0 text-white-50 small"><i class="fa-solid fa-location-dot me-1"></i>${fn:escapeXml(ticket.venueName)}</p>
            </div>

            <!-- QR Code Section -->
            <div class="qr-section">
                <img src="${pageContext.request.contextPath}/api/tickets/${fn:escapeXml(ticket.id)}/qr" alt="Mã QR Vé" class="qr-img shadow-sm mb-2" id="ticketQrImage">
                <p class="small text-muted mb-0">Xuất trình mã QR này tại quầy check-in của sự kiện</p>
            </div>

            <!-- Details Section -->
            <div class="ticket-body">
                <div class="info-row">
                    <span class="info-label">Trạng thái</span>
                    <span class="info-val">
                        <c:choose>
                            <c:when test="${ticket.status == 'ACTIVE'}">
                                <span class="badge bg-success-subtle text-success px-2 py-1 rounded-pill">Đang hiệu lực</span>
                            </c:when>
                            <c:when test="${ticket.status == 'USED'}">
                                <span class="badge bg-secondary-subtle text-secondary px-2 py-1 rounded-pill">Đã sử dụng</span>
                            </c:when>
                            <c:when test="${ticket.status == 'REFUNDED'}">
                                <span class="badge bg-danger-subtle text-danger px-2 py-1 rounded-pill">Đã hoàn tiền</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-light text-dark px-2 py-1 rounded-pill">${fn:escapeXml(ticket.status)}</span>
                            </c:otherwise>
                        </c:choose>
                    </span>
                </div>

                <div class="info-row">
                    <span class="info-label">Thời gian</span>
                    <span class="info-val">${fn:escapeXml(ticket.startTime)}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Khu vực</span>
                    <span class="info-val">${fn:escapeXml(not empty ticket.zoneName ? ticket.zoneName : 'Tiêu chuẩn')}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Vị trí chỗ ngồi</span>
                    <span class="info-val text-primary">${fn:escapeXml(not empty ticket.seatLabel ? ticket.seatLabel : 'Vé tự do / Standing')}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Địa điểm</span>
                    <span class="info-val small text-muted text-end" style="max-width: 250px;">
                        ${fn:escapeXml(not empty ticket.venueAddress ? ticket.venueAddress : ticket.venueName)}
                    </span>
                </div>

                <div class="info-row pt-2 border-top">
                    <span class="info-label">Giá vé thanh toán</span>
                    <span class="info-val fs-5 text-primary">
                        <fmt:formatNumber value="${ticket.paidAmount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                    </span>
                </div>



                <div class="mt-4 d-grid gap-2">
                    <a href="${pageContext.request.contextPath}/api/tickets/${fn:escapeXml(ticket.id)}/qr" download="ticket-${fn:escapeXml(ticket.ticketCode)}-qr.png" class="btn btn-outline-primary">
                        <i class="fa-solid fa-download me-1"></i> Tải ảnh mã QR
                    </a>
                </div>
            </div>
        </div>
    </div>
</main>


<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>
</html>
