<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu — TicketsCenter</title>

    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page bg-light min-h-screen d-flex flex-column justify-content-between">
    <!-- Header -->
    <nav class="navbar navbar-custom">
        <div class="container d-flex align-items-center justify-content-between">
            <a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">
                TicketsCenter
            </a>
            <a href="${pageContext.request.contextPath}/" class="small fw-semibold text-muted text-decoration-none">
                <i class="fa-solid fa-house me-1"></i> Trang chủ
            </a>
        </div>
    </nav>

    <!-- Central Auth Card -->
    <main class="container my-auto py-5">
        <div class="auth-container shadow-sm">
            <!-- Auth Switcher Tabs -->
            <div class="auth-tabs">
                <a href="${pageContext.request.contextPath}/auth/login" class="auth-tab-btn"><i
                        class="fa-solid fa-arrow-left me-1"></i> Đăng nhập</a>
                <span class="auth-tab-btn active">Đặt lại mật khẩu</span>
            </div>


            <!-- Reset Password Content -->
            <div>
                <h2 class="h4 fw-bold mb-1 text-dark">Thiết lập mật khẩu mới</h2>
                <p class="small text-muted mb-4">
                    Nhập mật khẩu mới của bạn (tối thiểu 12 ký tự) để hoàn tất việc khôi phục quyền truy cập.
                </p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="resetForm" onsubmit="handleResetPassword(event)">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                    <input type="hidden" id="tokenInput" value="${fn:escapeXml(token)}">

                    <div class="mb-3">
                        <label class="filter-label" for="newPassword">Mật khẩu mới (tối thiểu 12 ký tự) *</label>
                        <input type="password" id="newPassword"
                            class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12"
                            maxlength="256" placeholder="Ít nhất 12 ký tự" autofocus>
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="confirmPassword">Xác nhận mật khẩu mới *</label>
                        <input type="password" id="confirmPassword"
                            class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12"
                            maxlength="256" placeholder="Nhập lại mật khẩu mới">
                    </div>

                    <button type="submit" id="btnSubmit" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Cập nhật mật khẩu mới <i class="fa-solid fa-key ms-1"></i>
                    </button>
                </form>

                <div class="mt-4 text-center">
                    <a href="${pageContext.request.contextPath}/auth/login"
                        class="small text-decoration-none text-muted">&larr; Quay lại đăng nhập</a>
                </div>
            </div>
        </div>
    </main>

    <!-- Footer -->
    <footer class="mt-auto py-4 bg-white border-top">
        <div class="container text-center small text-muted">
            &copy; 2026 TicketsCenter. Bảo mật thông tin phiên đăng nhập và tài khoản người dùng.
        </div>
    </footer>

    <script defer src="${pageContext.request.contextPath}/assets/js/auth/reset-password.js"></script>
</body>
</html>
