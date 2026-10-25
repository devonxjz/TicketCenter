<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="vn.ticketscenter.fulfillment.refund.dto.RefundPageDto,java.math.BigDecimal,java.text.NumberFormat,java.util.Locale" %>
<%!
    private static String h(Object v) {
        return v == null
                ? ""
                : v.toString()
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                        .replace("'", "&#39;");
    }

    private static String status(String s) {
        return switch (s) {
            case "REQUESTED" -> "Chờ duyệt";
            case "APPROVED" -> "Đã duyệt, chờ chuyển tiền";
            case "PROCESSING" -> "Đang chuyển tiền";
            case "COMPLETED" -> "Đã hoàn tiền";
            case "REJECTED" -> "Bị từ chối";
            case "RETRYABLE" -> "Chuyển tiền thất bại, cần xử lý";
            case "NEEDS_RECONCILIATION" -> "Đang xác minh giao dịch";
            default -> s;
        };
    }
%>
<%
    var result = (RefundPageDto) request.getAttribute("refundPage");
%>
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
    <main class="refund-container refund-panel" data-base="<%=h(request.getContextPath())%>">
        <a href="<%=h(request.getContextPath())%>/refund-requests">+ Gửi yêu cầu hoàn vé</a>
        <h1>Yêu cầu hoàn vé của tôi</h1>
        <p><%=result.total()%> yêu cầu · trang <%=result.page()%></p>
        <%
            if (result.items().isEmpty()) {
        %>
        <p>Chưa có yêu cầu trên trang này.</p>
        <%
            }
            for (var r : result.items()) {
        %>
        <article>
            <span class="badge <%=h(r.status())%>"><%=h(status(r.status()))%></span>
            <h2><%=h(r.eventName())%></h2>
            <p><%=r.ticketCount()%> vé ·
                <%=
                    h(
                            NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"))
                                    .format(new BigDecimal(r.totalRefundAmount())))
                %>
                ₫</p>
            <small><time class="tc-local-time" datetime="<%=h(r.createdAt())%>"><%=h(r.createdAt())%></time></small>
            <pre>Lý do: <%=h(r.reason())%></pre>
            <%
                if (r.rejectionReason() != null) {
            %>
            <pre>Lý do từ chối: <%=h(r.rejectionReason())%></pre>
            <%
                }
            %>
            <button type="button" data-id="<%=h(r.refundId())%>">Xem tiến độ và vé</button>
        </article>
        <%
            }
        %>
        <nav data-tc-pagination data-page="<%=result.page()%>" data-total="<%=result.total()%>"
            data-page-size="<%=result.pageSize()%>" aria-label="Phân trang hoàn vé"></nav>
        <dialog id="dialog">
            <h2>Tiến độ hoàn vé</h2>
            <div id="details" role="status"></div>
            <button id="close">Đóng</button>
        </dialog>
    </main>
    <script src="${pageContext.request.contextPath}/assets/js/refund/my-refunds.js"></script>
    <jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body>
</html>
