<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<% response.setHeader("Cache-Control", "no-store"); %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>Hồ sơ tài khoản | TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <style>[hidden]{display:none!important}</style>
</head>
<body class="bg-light" data-live-page="profile" data-context="${pageContext.request.contextPath}">
<jsp:include page="/WEB-INF/views/layout/header.jsp" />
<main class="container my-5" style="max-width: 860px;">
    <p id="live-status" role="status" aria-live="polite" class="text-muted">Đang tải hồ sơ…</p>
    <div class="panel-box bg-white mb-4">
        <div class="d-flex align-items-center gap-3">
            <div class="profile-avatar" id="profile-initials"></div>
            <div>
                <h1 class="h4 fw-bold mb-1" id="profile-name"></h1>
                <span class="status-pill status-dark" id="profile-role"></span>
            </div>
        </div>
    </div>
    <div class="panel-box bg-white mb-4">
        <h2 class="h6 fw-bold mb-3">Thông tin đăng nhập</h2>
        <dl class="row mb-0">
            <dt class="col-sm-4 text-muted fw-normal">Email</dt><dd class="col-sm-8" id="profile-email"></dd>
            <dt class="col-sm-4 text-muted fw-normal">Tên đăng nhập</dt><dd class="col-sm-8" id="profile-username"></dd>
        </dl>
        <a class="btn btn-sm btn-outline-dark rounded-0 mt-3" href="${pageContext.request.contextPath}/auth/forgot-password">Đổi mật khẩu</a>
    </div>
    <div class="panel-box bg-white mb-4">
        <h2 class="h6 fw-bold mb-3">Thông tin cá nhân</h2>
        <form id="profile-form" class="row g-3">
            <div class="col-md-6"><label class="form-label" for="profile-full-name">Họ và tên</label><input id="profile-full-name" class="form-control rounded-0" maxlength="200" required></div>
            <div class="col-md-6"><label class="form-label" for="profile-phone">Số điện thoại</label><input id="profile-phone" class="form-control rounded-0" maxlength="30" inputmode="tel"></div>
            <div class="col-12"><button class="btn btn-dark rounded-0">Lưu thay đổi</button> <span id="profile-saved" class="small text-success ms-2" role="status" aria-live="polite"></span></div>
        </form>
    </div>
    <div class="panel-box bg-white mb-4">
        <h2 class="h6 fw-bold mb-3">Tổ chức tham gia</h2>
        <div class="table-responsive">
            <table class="table-custom">
                <thead><tr><th>Tổ chức</th><th>Vai trò</th><th>Trạng thái</th></tr></thead>
                <tbody id="profile-memberships"></tbody>
            </table>
        </div>
    </div>
    <div class="d-flex flex-wrap gap-2">
        <a class="btn btn-outline-dark rounded-0" href="${pageContext.request.contextPath}/me/orders">Đơn hàng của tôi</a>
        <a class="btn btn-outline-dark rounded-0" href="${pageContext.request.contextPath}/me/tickets">Vé của tôi</a>
        <a class="btn btn-outline-dark rounded-0" href="${pageContext.request.contextPath}/my-refunds">Yêu cầu hoàn vé</a>
    </div>
    <noscript>Vui lòng bật JavaScript để xem hồ sơ.</noscript>
</main>
<jsp:include page="/WEB-INF/views/layout/footer.jsp" />
<script defer src="${pageContext.request.contextPath}/assets/js/live-pages.js"></script>
</body>
</html>
