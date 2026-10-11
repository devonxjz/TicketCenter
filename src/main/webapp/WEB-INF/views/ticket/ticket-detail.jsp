<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Vé Điện Tử - ${ticket.ticketCode} - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
    <style>
        body {
            background-color: #f1f5f9;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #1e293b;
        }
        .ticket-wrapper {
            max-width: 500px;
            margin: 40px auto;
        }
        .ticket-pass {
            background: #ffffff;
            border-radius: 20px;
            box-shadow: 0 15px 35px -5px rgba(0, 0, 0, 0.08);
            border: 1px solid #e2e8f0;
            overflow: hidden;
            position: relative;
        }
        .ticket-header {
            background: linear-gradient(135deg, #1e3a8a 0%, #2563eb 100%);
            color: #ffffff;
            padding: 24px;
            text-align: center;
        }
        .qr-section {
            background-color: #ffffff;
            padding: 30px 20px;
            text-align: center;
            border-bottom: 2px dashed #e2e8f0;
            position: relative;
        }
        .qr-section::before, .qr-section::after {
            content: '';
            position: absolute;
            bottom: -12px;
            width: 24px;
            height: 24px;
            background-color: #f1f5f9;
            border-radius: 50%;
        }
        .qr-section::before { left: -12px; }
        .qr-section::after { right: -12px; }
        .qr-img {
            width: 220px;
            height: 220px;
            border: 2px solid #e2e8f0;
            border-radius: 12px;
            padding: 8px;
            background: #ffffff;
        }
        .ticket-body {
            padding: 24px;
        }
        .info-row {
            display: flex;
            justify-content: space-between;
            margin-bottom: 12px;
            font-size: 0.95rem;
        }
        .info-label {
            color: #64748b;
        }
        .info-val {
            font-weight: 600;
            color: #0f172a;
            text-align: right;
        }
    </style>
</head>
<body>

<div class="container">
    <div class="ticket-wrapper">
        <div class="mb-3 d-flex justify-content-between align-items-center">
            <a href="${pageContext.request.contextPath}/me/tickets" class="btn btn-outline-secondary btn-sm">
                <i class="fa-solid fa-arrow-left me-1"></i> Danh sách vé
            </a>
            <span class="badge bg-secondary font-monospace">${ticket.ticketCode}</span>
        </div>

        <div class="ticket-pass">
            <!-- Header -->
            <div class="ticket-header">
                <span class="badge bg-white text-primary mb-2 px-3 py-1 fw-bold rounded-pill">VÉ ĐIỆN TỬ</span>
                <h4 class="fw-bold mb-1">${ticket.eventTitle}</h4>
                <p class="mb-0 text-white-50 small"><i class="fa-solid fa-location-dot me-1"></i>${ticket.venueName}</p>
            </div>

            <!-- QR Code Section -->
            <div class="qr-section">
                <img src="${pageContext.request.contextPath}/api/tickets/${ticket.id}/qr" alt="Mã QR Vé" class="qr-img shadow-sm mb-2" id="ticketQrImage">
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
                                <span class="badge bg-light text-dark px-2 py-1 rounded-pill">${ticket.status}</span>
                            </c:otherwise>
                        </c:choose>
                    </span>
                </div>

                <div class="info-row">
                    <span class="info-label">Thời gian</span>
                    <span class="info-val">${ticket.startTime}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Khu vực</span>
                    <span class="info-val">${not empty ticket.zoneName ? ticket.zoneName : 'Tiêu chuẩn'}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Vị trí chỗ ngồi</span>
                    <span class="info-val text-primary">${not empty ticket.seatLabel ? ticket.seatLabel : 'Vé tự do / Standing'}</span>
                </div>

                <div class="info-row">
                    <span class="info-label">Địa điểm</span>
                    <span class="info-val small text-muted text-end" style="max-width: 250px;">
                        ${not empty ticket.venueAddress ? ticket.venueAddress : ticket.venueName}
                    </span>
                </div>

                <div class="info-row pt-2 border-top">
                    <span class="info-label">Giá vé thanh toán</span>
                    <span class="info-val fs-5 text-primary">
                        <fmt:formatNumber value="${ticket.paidAmount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                    </span>
                </div>

                <c:if test="${not empty ticket.orderCode}">
                    <div class="info-row">
                        <span class="info-label">Mã đơn hàng</span>
                        <span class="info-val font-monospace small text-muted">${ticket.orderCode}</span>
                    </div>
                </c:if>

                <div class="mt-4 d-grid gap-2">
                    <a href="${pageContext.request.contextPath}/api/tickets/${ticket.id}/qr" download="ticket-${ticket.ticketCode}-qr.png" class="btn btn-outline-primary">
                        <i class="fa-solid fa-download me-1"></i> Tải ảnh mã QR
                    </a>
                </div>
            </div>
        </div>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
