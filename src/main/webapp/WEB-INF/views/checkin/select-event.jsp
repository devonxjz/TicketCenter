<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="activeTab" value="checkin" scope="request" />
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chọn sự kiện soát vé | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/admin-ui.css">
</head>
<body class="checkin-body checkin-workspace">
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <div class="dashboard-layout">
        <c:choose>
            <c:when test="${sessionScope.TC_CURRENT_USER.isAdmin()}">
                <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp" />
            </c:when>
            <c:otherwise>
                <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
            </c:otherwise>
        </c:choose>
        <main id="main-content" class="dashboard-content">
            <h1>Chọn sự kiện soát vé</h1>
            <p class="hint">Chọn sự kiện của tổ chức để bắt đầu ca làm việc.</p>
            <div class="toolbar">
                <span>Danh sách sự kiện</span>
                <button type="button" id="reload">Làm mới</button>
            </div>
            <div id="notice" role="status" aria-live="polite"></div>
            <div id="events" aria-live="polite">
                <p>Đang tải sự kiện…</p>
            </div>
            <nav class="tc-pagination" aria-label="Chuyển trang">
                <button id="event-previous" type="button" class="tc-page-button" aria-label="Trang trước"
                    disabled>‹</button>
                <span id="event-page"></span>
                <button id="event-next" type="button" class="tc-page-button" aria-label="Trang sau" disabled>›</button>
            </nav>
            <p class="hint">Giờ hiển thị theo thiết bị của bạn. Cổng mở trước giờ bắt đầu 60 phút và đóng khi sự kiện
                kết thúc.</p>
        </main>
    </div>
    <script defer src="${pageContext.request.contextPath}/assets/js/checkin/select-event.js"></script>
</body>
</html>
