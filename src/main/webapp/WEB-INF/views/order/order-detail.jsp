<%@ page contentType="text/html;charset=UTF-8" language="java" %>
    <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
        <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
                <!DOCTYPE html>
                <html lang="vi">

                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Đơn vé ${fn:escapeXml(order.eventTitle)} - TicketsCenter</title>

                    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
                    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
                </head>

                <body class="buyer-page">
                    <jsp:include page="/WEB-INF/views/layout/header.jsp" />

                    <main class="container py-5 buyer-detail">
                        <div class="d-flex justify-content-between align-items-center mb-4">
                            <div>
                                <a href="${pageContext.request.contextPath}/me/orders"
                                    class="btn btn-outline-secondary btn-sm mb-2">
                                    <i class="fa-solid fa-arrow-left me-1"></i> Danh sách đơn hàng
                                </a>
                                <h1 class="h3 fw-bold mb-0">Đơn vé ${fn:escapeXml(order.eventTitle)}</h1>
                            </div>
                            <div>
                                <c:choose>
                                    <c:when test="${order.status == 'PAID'}">
                                        <span
                                            class="badge bg-success-subtle text-success px-3 py-2 rounded-pill fs-6">Đã
                                            thanh toán</span>
                                    </c:when>
                                    <c:when test="${order.status == 'PENDING_PAYMENT'}">
                                        <span
                                            class="badge bg-warning-subtle text-warning px-3 py-2 rounded-pill fs-6">Chờ
                                            thanh toán</span>
                                    </c:when>
                                    <c:when test="${order.status == 'CANCELLED'}">
                                        <span
                                            class="badge bg-secondary-subtle text-secondary px-3 py-2 rounded-pill fs-6">Đã
                                            hủy</span>
                                    </c:when>
                                    <c:when test="${order.status == 'EXPIRED'}">
                                        <span class="badge bg-danger-subtle text-danger px-3 py-2 fs-6">Đã hết hạn</span>
                                    </c:when>
                                    <c:otherwise>
                                        <span
                                            class="badge bg-danger-subtle text-danger px-3 py-2 rounded-pill fs-6">${fn:escapeXml(order.status)}</span>
                                    </c:otherwise>
                                </c:choose>
                            </div>
                        </div>

                        <!-- Thông tin sự kiện -->
                        <div class="detail-card">
                            <h5 class="fw-bold mb-3"><i class="fa-solid fa-calendar-check text-primary me-2"></i>Thông
                                Tin Sự Kiện</h5>
                            <h4 class="fw-bold mb-1">${fn:escapeXml(order.eventTitle)}</h4>
                            <p class="text-muted small mb-2"><i
                                    class="fa-solid fa-location-dot me-1"></i>${fn:escapeXml(order.venueName)} -
                                ${fn:escapeXml(order.venueAddress)}</p>
                            <p class="text-muted small mb-0"><i class="fa-regular fa-clock me-1"></i>Thời gian:
                                <time class="tc-local-time" datetime="${fn:escapeXml(order.startTime)}">${fn:escapeXml(order.startTime)}</time></p>
                        </div>

                        <!-- Chi tiết các vé trong đơn hàng -->
                        <div class="detail-card">
                            <h5 class="fw-bold mb-3"><i class="fa-solid fa-ticket text-primary me-2"></i>Danh Sách Vé
                                Mua</h5>
                            <div class="table-responsive">
                                <table class="table align-middle">
                                    <thead>
                                        <tr class="text-muted small">
                                            <th>Khu vực / Chỗ ngồi</th>
                                            <th class="text-center">Số lượng</th>
                                            <th class="text-end">Đơn giá</th>
                                            <th class="text-end">Thành tiền</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <c:forEach var="item" items="${order.items}">
                                            <tr>
                                                <td>
                                                    <strong class="d-block">${fn:escapeXml(item.zoneName)}</strong>
                                                    <span class="small text-muted">${fn:escapeXml(not empty
                                                        item.seatLabel ? item.seatLabel : 'Vé tự do / Standing')}</span>
                                                </td>
                                                <td class="text-center">${fn:escapeXml(item.quantity)}</td>
                                                <td class="text-end">
                                                    <fmt:formatNumber value="${item.unitPrice}" type="currency"
                                                        currencySymbol="₫" maxFractionDigits="0" />
                                                </td>
                                                <td class="text-end fw-bold">
                                                    <fmt:formatNumber value="${item.lineTotal}" type="currency"
                                                        currencySymbol="₫" maxFractionDigits="0" />
                                                </td>
                                            </tr>
                                        </c:forEach>
                                    </tbody>
                                </table>
                            </div>

                            <div class="mt-4 pt-3 border-top">
                                <div class="d-flex justify-content-between mb-2">
                                    <span class="text-muted">Tạm tính:</span>
                                    <span>
                                        <fmt:formatNumber value="${order.subtotalAmount}" type="currency"
                                            currencySymbol="₫" maxFractionDigits="0" />
                                    </span>
                                </div>
                                <c:if test="${not empty order.couponCode}">
                                    <div class="d-flex justify-content-between mb-2 text-success">
                                        <span>Mã giảm giá (${fn:escapeXml(order.couponCode)}):</span>
                                        <span>-
                                            <fmt:formatNumber value="${order.discountAmount}" type="currency"
                                                currencySymbol="₫" maxFractionDigits="0" />
                                        </span>
                                    </div>
                                </c:if>
                                <div class="d-flex justify-content-between fs-5 fw-bold text-primary pt-2 border-top">
                                    <span>Tổng cộng:</span>
                                    <span>
                                        <fmt:formatNumber value="${order.totalAmount}" type="currency"
                                            currencySymbol="₫" maxFractionDigits="0" />
                                    </span>
                                </div>
                            </div>
                        </div>

                        <!-- Danh sách vé đã phát hành nếu đã thanh toán -->
                        <c:if test="${not empty order.tickets}">
                            <div class="detail-card">
                                <h5 class="fw-bold mb-3"><i class="fa-solid fa-qrcode text-primary me-2"></i>Vé & Mã QR
                                    Đã Phát Hành</h5>
                                <div class="list-group">
                                    <c:forEach var="t" items="${order.tickets}" varStatus="ticketPosition">
                                        <div
                                            class="list-group-item d-flex justify-content-between align-items-center p-3">
                                            <div>
                                                <span class="font-monospace fw-bold">Vé ${ticketPosition.count}</span>
                                                <span class="badge bg-light text-dark ms-2">
                                                    <c:choose>
                                                        <c:when test="${t.status == 'ACTIVE'}">Còn hiệu lực</c:when>
                                                        <c:when test="${t.status == 'USED'}">Đã sử dụng</c:when>
                                                        <c:when test="${t.status == 'REFUND_PENDING'}">Chờ hoàn tiền
                                                        </c:when>
                                                        <c:when test="${t.status == 'REFUNDED'}">Đã hoàn tiền</c:when>
                                                        <c:otherwise>Không còn hiệu lực</c:otherwise>
                                                    </c:choose>
                                                </span>
                                            </div>
                                            <a href="${pageContext.request.contextPath}/tickets/${fn:escapeXml(t.id)}"
                                                class="btn btn-outline-primary btn-sm">
                                                <i class="fa-solid fa-qrcode me-1"></i> Xem QR vé
                                            </a>
                                        </div>
                                    </c:forEach>
                                </div>
                            </div>
                        </c:if>
                        <c:if test="${order.status == 'PENDING_PAYMENT'}">
                            <div class="detail-card">
                                <h2 class="h5 fw-bold">Hoàn tất đơn vé</h2>
                                <p class="text-muted">Tiếp tục với vé đang giữ để kiểm tra trạng thái và thanh toán.</p>
                                <a href="${pageContext.request.contextPath}/checkout" class="btn btn-primary">Tiếp tục
                                    thanh toán</a>
                            </div>
                        </c:if>
                        <c:if test="${order.status == 'PAID'}"><a
                                href="${pageContext.request.contextPath}/refund-requests?orderId=${fn:escapeXml(order.id)}"
                                class="btn btn-outline-dark">Yêu cầu hoàn vé</a></c:if>
                    </main>


                    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
                </body>

                </html>
