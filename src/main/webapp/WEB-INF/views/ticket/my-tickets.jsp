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
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
    <style>
        body {
            background-color: #f8fafc;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #1e293b;
        }
        .ticket-card {
            background: #ffffff;
            border-radius: 14px;
            border: 1px solid #e2e8f0;
            transition: transform 0.2s, box-shadow 0.2s;
            overflow: hidden;
        }
        .ticket-card:hover {
            transform: translateY(-2px);
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.06);
        }
        .badge-status {
            font-size: 0.85rem;
            padding: 6px 12px;
            border-radius: 20px;
            font-weight: 600;
        }
        .btn-qr {
            background-color: #2563eb;
            color: white;
            font-weight: 600;
            border-radius: 8px;
            transition: all 0.2s;
        }
        .btn-qr:hover {
            background-color: #1d4ed8;
            color: white;
        }
        .empty-state {
            padding: 60px 20px;
            text-align: center;
            background: white;
            border-radius: 16px;
            border: 1px dashed #cbd5e1;
        }
    </style>
</head>
<body>

<div class="container py-5">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
            <h2 class="fw-bold mb-1"><i class="fa-solid fa-ticket text-primary me-2"></i>Vé Của Tôi</h2>
            <p class="text-muted mb-0">Quản lý các vé sự kiện bạn đã mua và xuất trình mã QR khi check-in</p>
        </div>
        <a href="${pageContext.request.contextPath}/" class="btn btn-outline-secondary">
            <i class="fa-solid fa-house me-1"></i> Trang chủ
        </a>
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
            <div class="row g-4">
                <c:forEach var="t" items="${tickets}">
                    <div class="col-md-6 col-lg-4">
                        <div class="ticket-card p-4 h-100 d-flex flex-column justify-content-between">
                            <div>
                                <div class="d-flex justify-content-between align-items-start mb-2">
                                    <span class="text-muted small font-monospace">${fn:escapeXml(t.ticketCode)}</span>
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

                                <div class="text-muted small mb-1">
                                    <i class="fa-regular fa-clock me-1"></i>
                                    <span>${fn:escapeXml(t.startTime)}</span>
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
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
