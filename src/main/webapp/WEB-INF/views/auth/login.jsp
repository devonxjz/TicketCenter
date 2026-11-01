<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:url var="loginUrl" value="/auth/login">
    <c:if test="${not empty param.redirect}">
        <c:param name="redirect" value="${param.redirect}" />
    </c:if>
</c:url>
<c:url var="registerUrl" value="/auth/register">
    <c:if test="${not empty param.redirect}">
        <c:param name="redirect" value="${param.redirect}" />
    </c:if>
</c:url>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng nhập — TicketsCenter</title>

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
                <a href="${fn:escapeXml(loginUrl)}" class="auth-tab-btn active">Đăng nhập</a>
                <a href="${fn:escapeXml(registerUrl)}" class="auth-tab-btn">Đăng ký</a>
            </div>

            <!-- Login Form -->
            <div id="formLogin">
                <h2 class="h4 fw-bold mb-1 text-dark">Chào mừng trở lại</h2>
                <p class="small text-muted mb-4">Đăng nhập để theo dõi vé đã đặt và quản lý thông tin cá nhân.</p>

                <c:if test="${param.registered == 'true'}">
                    <div class="alert alert-success mb-3" role="alert">
                        <i class="fa-solid fa-circle-check me-2"></i>Kích hoạt tài khoản thành công! Vui lòng đăng nhập
                        để tiếp tục.
                    </div>
                </c:if>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="loginForm" action="${fn:escapeXml(loginUrl)}" method="post">
                    <input type="hidden" id="login-csrf" name="_csrf"
                        value="${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}">
                    <div class="mb-3">
                        <label class="filter-label" for="identifier">Email hoặc Tên tài khoản</label>
                        <input type="text" id="identifier" name="identifier" autocomplete="username"
                            class="form-control rounded-0 border-secondary-subtle py-2" required autofocus>
                    </div>

                    <div class="mb-4">
                        <div class="d-flex justify-content-between align-items-center">
                            <label class="filter-label" for="password">Mật khẩu</label>
                            <a href="${pageContext.request.contextPath}/auth/forgot-password"
                                class="small text-danger text-decoration-none">Quên mật khẩu?</a>
                        </div>
                        <input type="password" id="password" name="password" autocomplete="current-password"
                            class="form-control rounded-0 border-secondary-subtle py-2" required>
                    </div>

                    <button type="submit" id="btnLogin" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Đăng nhập
                    </button>

                    <div class="text-center mb-4">
                        <span class="small text-muted">Chưa có tài khoản?</span>
                        <a href="${fn:escapeXml(registerUrl)}"
                            class="small fw-bold text-dark text-decoration-underline ms-1">Tạo tài khoản</a>
                    </div>
                </form>

            </div>
        </div>
    </main>

    <!-- Footer -->
    <footer class="mt-auto py-4 bg-white border-top">
        <div class="container text-center small text-muted">
            &copy; 2026 TicketsCenter. Bảo mật thông tin phiên đăng nhập và tài khoản người dùng.
        </div>
    </footer>

    <script defer src="${pageContext.request.contextPath}/assets/js/auth/login.js"></script>
</body>
</html>
