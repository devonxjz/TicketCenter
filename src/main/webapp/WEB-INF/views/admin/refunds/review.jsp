<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="csrf-token" content="<c:out value='${csrf}'/>">
    <title>Xử lý hoàn tiền · TicketCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="<c:url value='/assets/css/admin-ui.css'/>">
    <script defer src="<c:url value='/assets/refund/admin-refunds.js'/>"></script>
</head>
<body class="admin-workspace refund-portal" data-page="review"
    data-context="<c:out value='${pageContext.request.contextPath}'/>" data-refund="<c:out value='${refundId}'/>">
    <a class="portal-skip" href="#main-content">Đến nội dung chính</a>
    <jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />
    <div class="dashboard-layout">
        <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp">
            <jsp:param name="active" value="refund-approval" />
        </jsp:include>
        <main class="dashboard-content" id="main-content">
            <a href="<c:url value='/admin/refund-requests'/>">← Danh sách hoàn tiền</a>
            <h1>Xử lý yêu cầu hoàn tiền</h1>
            <p id="feedback" role="status" aria-live="polite">Đang tải hồ sơ…</p>
            <button id="reload" type="button" hidden>Tải lại</button>
            <section id="detail" hidden>
                <h2>Hồ sơ</h2>
                <dl id="facts"></dl>
                <h2>Vé trong yêu cầu</h2>
                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Vé</th>
                                <th>Ghế / Khu vực</th>
                                <th>Giá đã trả</th>
                                <th>Trạng thái</th>
                            </tr>
                        </thead>
                        <tbody id="tickets"></tbody>
                    </table>
                </div>
                <h2>Lịch sử chuyển tiền</h2>
                <p id="noAttempts" hidden>Chưa có lần chuyển tiền nào.</p>
                <div class="table-wrap">
                    <table>
                        <thead>
                            <tr>
                                <th>Lần</th>
                                <th>Trạng thái</th>
                                <th>Tạo lúc</th>
                                <th>Xử lý lúc</th>
                            </tr>
                        </thead>
                        <tbody id="attempts"></tbody>
                    </table>
                </div>
                <p id="stateHint"></p>
                <div id="actions">
                    <button id="approve" type="button" hidden>Phê duyệt</button><button id="reject" type="button"
                        class="danger" hidden>Từ chối</button><button id="retry" type="button" hidden>Thử chuyển tiền
                        lại</button>
                </div>
            </section>
            <dialog id="decisionDialog" aria-labelledby="dialogTitle">
                <form id="decisionForm">
                    <h2 id="dialogTitle"></h2>
                    <p id="dialogHint"></p>
                    <label id="inputLabel" for="decisionText"></label><textarea id="decisionText"
                        rows="4"></textarea>
                    <p id="dialogError" role="alert"></p>
                    <div class="actions">
                        <button id="cancel" type="button">Đóng</button><button id="confirm" type="submit">Xác
                            nhận</button>
                    </div>
                </form>
            </dialog>
        </main>
    </div>
    <script defer src="<c:url value='/assets/js/admin-notifications.js'/>"></script>
</body>
</html>
