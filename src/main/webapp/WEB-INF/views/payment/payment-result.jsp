<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Kết Quả Thanh Toán - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
    <style>
        body {
            background-color: #f8fafc;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #1e293b;
        }
        .result-container {
            max-width: 600px;
            margin: 60px auto;
            padding: 20px;
        }
        .result-card {
            background: #ffffff;
            border-radius: 16px;
            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.01);
            overflow: hidden;
            border: 1px solid #e2e8f0;
        }
        .header-banner {
            padding: 36px 24px;
            text-align: center;
        }
        .header-banner.success {
            background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
            color: #065f46;
        }
        .header-banner.failure {
            background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
            color: #991b1b;
        }
        .status-icon {
            font-size: 56px;
            margin-bottom: 12px;
        }
        .detail-row {
            display: flex;
            justify-content: space-between;
            padding: 12px 0;
            border-bottom: 1px dashed #e2e8f0;
        }
        .detail-row:last-child {
            border-bottom: none;
        }
        .detail-label {
            color: #64748b;
            font-weight: 500;
        }
        .detail-value {
            font-weight: 600;
            color: #0f172a;
            text-align: right;
        }
        .btn-custom-primary {
            background-color: #2563eb;
            color: white;
            font-weight: 600;
            border-radius: 10px;
            padding: 12px 24px;
            border: none;
            transition: all 0.2s;
        }
        .btn-custom-primary:hover {
            background-color: #1d4ed8;
            color: white;
            transform: translateY(-1px);
        }
        .btn-custom-secondary {
            background-color: #f1f5f9;
            color: #334155;
            font-weight: 600;
            border-radius: 10px;
            padding: 12px 24px;
            border: 1px solid #cbd5e1;
            transition: all 0.2s;
        }
        .btn-custom-secondary:hover {
            background-color: #e2e8f0;
            color: #1e293b;
        }
    </style>
</head>
<body>

<div class="result-container">
    <div class="result-card">
        <c:choose>
            <c:when test="${success}">
                <div class="header-banner success">
                    <div class="status-icon text-success">
                        <i class="fa-solid fa-circle-check"></i>
                    </div>
                    <h3 class="fw-bold mb-1">Thanh toán thành công!</h3>
                    <p class="mb-0 text-muted">Cảm ơn bạn đã mua vé trên TicketsCenter</p>
                </div>
            </c:when>
            <c:otherwise>
                <div class="header-banner failure">
                    <div class="status-icon text-danger">
                        <i class="fa-solid fa-circle-xmark"></i>
                    </div>
                    <h3 class="fw-bold mb-1">Giao dịch chưa hoàn tất</h3>
                    <p class="mb-0 text-muted">${fn:escapeXml(message)}</p>
                </div>
            </c:otherwise>
        </c:choose>

        <div class="p-4">
            <h5 class="fw-bold mb-3">Chi tiết giao dịch</h5>

            <div class="detail-row">
                <span class="detail-label">Mã giao dịch</span>
                <span class="detail-value">${fn:escapeXml(not empty txnRef ? txnRef : 'N/A')}</span>
            </div>

            <c:if test="${not empty orderId}">
                <div class="detail-row">
                    <span class="detail-label">Mã đơn hàng</span>
                    <span class="detail-value font-monospace">${fn:escapeXml(orderId)}</span>
                </div>
            </c:if>

            <div class="detail-row">
                <span class="detail-label">Số tiền</span>
                <span class="detail-value text-primary fs-5">
                    <fmt:formatNumber value="${amount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                </span>
            </div>

            <div class="detail-row">
                <span class="detail-label">Cổng thanh toán</span>
                <span class="detail-value">VNPAY Sandbox</span>
            </div>

            <div class="detail-row">
                <span class="detail-label">Trạng thái</span>
                <span class="detail-value">
                    <c:choose>
                        <c:when test="${success}">
                            <span class="badge bg-success-subtle text-success px-3 py-2 rounded-pill">Đã thanh toán</span>
                        </c:when>
                        <c:otherwise>
                            <span class="badge bg-danger-subtle text-danger px-3 py-2 rounded-pill">Chưa thanh toán</span>
                        </c:otherwise>
                    </c:choose>
                </span>
            </div>

            <div class="d-flex flex-column flex-sm-row gap-3 mt-4">
                <c:choose>
                    <c:when test="${success}">
                        <a href="${pageContext.request.contextPath}/me/tickets" class="btn btn-custom-primary flex-fill text-center">
                            <i class="fa-solid fa-ticket me-2"></i>Xem vé của tôi
                        </a>
                        <a href="${pageContext.request.contextPath}/" class="btn btn-custom-secondary flex-fill text-center">
                            <i class="fa-solid fa-house me-2"></i>Về trang chủ
                        </a>
                    </c:when>
                    <c:otherwise>
                        <c:if test="${not empty orderId}">
                            <a href="${pageContext.request.contextPath}/api/orders/${fn:escapeXml(orderId)}" class="btn btn-custom-primary flex-fill text-center">
                                <i class="fa-solid fa-arrow-rotate-right me-2"></i>Chi tiết đơn hàng
                            </a>
                        </c:if>
                        <a href="${pageContext.request.contextPath}/" class="btn btn-custom-secondary flex-fill text-center">
                            <i class="fa-solid fa-house me-2"></i>Về trang chủ
                        </a>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
