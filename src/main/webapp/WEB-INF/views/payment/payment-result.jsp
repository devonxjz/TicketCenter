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

    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>

<body class="buyer-page">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />

    <div class="result-container">
        <div class="result-card">
            <c:choose>
                <c:when test="${success}">
                    <div class="header-banner success">
                        <div class="status-icon text-success">
                            <i class="fa-solid fa-circle-check"></i>
                        </div>
                        <h3 class="fw-bold mb-1">Thanh toán thành công</h3>
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
                        <fmt:formatNumber value="${amount}" type="currency" currencySymbol="₫" maxFractionDigits="0" />
                    </span>
                </div>

                <div class="detail-row">
                    <span class="detail-label">Cổng thanh toán</span>
                    <span class="detail-value">payOS</span>
                </div>

                <div class="detail-row">
                    <span class="detail-label">Trạng thái</span>
                    <span class="detail-value">
                        <c:choose>
                            <c:when test="${success}">
                                <span class="badge bg-success-subtle text-success px-3 py-2 rounded-pill">Đã
                                    thanh toán</span>
                            </c:when>
                            <c:otherwise>
                                <span class="badge bg-danger-subtle text-danger px-3 py-2 rounded-pill">Chưa
                                    thanh toán</span>
                            </c:otherwise>
                        </c:choose>
                    </span>
                </div>

                <div class="d-flex flex-column flex-sm-row gap-3 mt-4">
                    <c:choose>
                        <c:when test="${success}">
                            <a href="${pageContext.request.contextPath}/me/tickets"
                                class="btn btn-custom-primary flex-fill text-center">
                                <i class="fa-solid fa-ticket me-2"></i>Xem vé của tôi
                            </a>
                            <a href="${pageContext.request.contextPath}/"
                                class="btn btn-custom-secondary flex-fill text-center">
                                <i class="fa-solid fa-house me-2"></i>Về trang chủ
                            </a>
                        </c:when>
                        <c:otherwise>
                            <c:if test="${not empty orderId}">
                                <a href="${pageContext.request.contextPath}/orders/${fn:escapeXml(orderId)}"
                                    class="btn btn-custom-primary flex-fill text-center">
                                    <i class="fa-solid fa-arrow-rotate-right me-2"></i>Chi tiết đơn hàng
                                </a>
                            </c:if>
                            <a href="${pageContext.request.contextPath}/"
                                class="btn btn-custom-secondary flex-fill text-center">
                                <i class="fa-solid fa-house me-2"></i>Về trang chủ
                            </a>
                        </c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>

    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>

</html>
