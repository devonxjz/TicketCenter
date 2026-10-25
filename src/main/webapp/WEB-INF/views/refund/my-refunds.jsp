<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<fmt:setLocale value="vi_VN" />
<c:set var="result" value="${requestScope.refundPage}" />
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Yêu cầu hoàn vé của tôi</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <main class="refund-container refund-panel">
        <a href="${pageContext.request.contextPath}/refund-requests">+ Gửi yêu cầu hoàn vé</a>
        <h1>Yêu cầu hoàn vé của tôi</h1>
        <p>${result.total()} yêu cầu · trang ${result.page()}</p>

        <c:if test="${empty result.items()}">
            <p>Chưa có yêu cầu trên trang này.</p>
        </c:if>

        <c:forEach var="r" items="${result.items()}">
            <article>
                <span class="badge ${fn:escapeXml(r.status())}"><c:out value="${r.statusLabel()}" /></span>
                <h2><c:out value="${r.eventName()}" /></h2>
                <p>
                    ${r.ticketCount()} vé ·
                    <fmt:formatNumber value="${r.totalRefundAmount()}" maxFractionDigits="0" /> ₫
                </p>
                <small>
                    <time class="tc-local-time" datetime="${r.createdAt()}">${r.createdAt()}</time>
                </small>
                <pre>Lý do: <c:out value="${r.reason()}" /></pre>
                <c:if test="${not empty r.rejectionReason()}">
                    <pre>Lý do từ chối: <c:out value="${r.rejectionReason()}" /></pre>
                </c:if>
                <button type="button" data-id="${r.refundId()}">Xem tiến độ và vé</button>
            </article>
        </c:forEach>

        <nav data-tc-pagination data-page="${result.page()}" data-total="${result.total()}"
            data-page-size="${result.pageSize()}" aria-label="Phân trang hoàn vé"></nav>

        <dialog id="dialog">
            <h2>Tiến độ hoàn vé</h2>
            <div id="details" role="status"></div>
            <button type="button" id="close">Đóng</button>
        </dialog>
    </main>
    <script defer src="${pageContext.request.contextPath}/assets/js/refund/my-refunds.js"></script>
    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>
</html>
