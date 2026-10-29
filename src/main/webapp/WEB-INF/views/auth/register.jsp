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
    <title>Đăng ký tài khoản — TicketsCenter</title>

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
            <!-- login / register tabs -->
            <div class="auth-tabs" id="authTabsHeader">
                <a href="${fn:escapeXml(loginUrl)}" class="auth-tab-btn">Đăng nhập</a>
                <a href="${fn:escapeXml(registerUrl)}" class="auth-tab-btn active">Đăng ký</a>
            </div>

            <!-- step 1: registration form -->
            <div id="formRegister">
                <h2 class="h4 fw-bold mb-1 text-dark">Tạo tài khoản mới</h2>
                <p class="small text-muted mb-4">Đăng ký tài khoản để mua vé nhanh chóng và quản lý đơn hàng.</p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="registerForm" action="${pageContext.request.contextPath}/api/auth/register" method="post">
                    <input type="hidden" name="_csrf"
                        value="${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}">
                    <div class="mb-3">
                        <label class="filter-label" for="registerUserName">Tên đăng nhập duy nhất (userName) *</label>
                        <input id="registerUserName" name="userName" type="text"
                            class="form-control rounded-0 border-secondary-subtle py-2" required minlength="3"
                            maxlength="100" autocomplete="username" placeholder="nguyenvana" autofocus>
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerFullName">Họ và tên *</label>
                        <input id="registerFullName" name="fullName" type="text"
                            class="form-control rounded-0 border-secondary-subtle py-2" required maxlength="200"
                            placeholder="Nguyễn Văn A">
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerEmail">Địa chỉ Email *</label>
                        <input id="registerEmail" name="email" type="email"
                            class="form-control rounded-0 border-secondary-subtle py-2" required
                            placeholder="email@example.com">
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerPassword">Mật khẩu bảo vệ (tối thiểu 12 ký tự)
                            *</label>
                        <input id="registerPassword" name="password" type="password"
                            class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12"
                            maxlength="256" autocomplete="new-password" placeholder="Tối thiểu 12 ký tự">
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="registerPasswordConfirm">Xác nhận mật khẩu *</label>
                        <input id="registerPasswordConfirm" type="password"
                            class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12"
                            maxlength="256" autocomplete="new-password" placeholder="Nhập lại mật khẩu">
                    </div>

                    <button type="submit" id="btnRegister" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Đăng ký tài khoản
                    </button>

                    <div class="text-center mb-2">
                        <span class="small text-muted">Đã có tài khoản?</span>
                        <a href="${fn:escapeXml(loginUrl)}"
                            class="small fw-bold text-dark text-decoration-underline ms-1">Đăng nhập</a>
                    </div>
                </form>
            </div>

            <!-- step 2: enter the OTP right here after registering -->
            <div id="formOtp" class="d-none">
                <div class="text-center mb-4">
                    <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle mb-3 tc-avatar-56">
                        <i class="fa-solid fa-envelope-circle-check fs-3"></i>
                    </div>
                    <h2 class="h4 fw-bold mb-1 text-dark">Xác thực tài khoản</h2>
                    <p class="small text-muted mb-1">Mã OTP kích hoạt đã được gửi đến email:</p>
                    <div class="fw-bold text-dark fs-6" id="displayRegisteredEmail"></div>
                    <p class="small text-muted mt-2">Vui lòng kiểm tra hộp thư (bao gồm cả thư mục Spam/Rác) và nhập mã
                        6 số bên dưới để hoàn tất.</p>
                </div>

                <div id="otpAlertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="otpVerifyForm">
                    <div class="mb-4">
                        <label class="filter-label" for="otpCodeInput">Mã OTP (6 chữ số) *</label>
                        <input type="text" id="otpCodeInput"
                            class="form-control rounded-0 border-secondary-subtle py-2 otp-input" placeholder="000000"
                            inputmode="numeric" pattern="[0-9]{6}" autocomplete="one-time-code" required>
                        <span class="small text-muted mt-1 d-block">Mã OTP có hiệu lực 5 phút; nhập sai quá 5 lần sẽ bị
                            hủy.</span>
                    </div>

                    <button type="submit" id="btnVerifyOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Xác nhận & Kích hoạt tài khoản
                    </button>
                </form>

                <div class="text-center mt-3">
                    <span id="cooldownText" class="small text-muted d-block mb-1"></span>
                    <button type="button" id="btnResendOtp"
                        class="btn btn-link text-decoration-none small text-dark p-0 fw-semibold" disabled>
                        Gửi lại mã OTP
                    </button>
                </div>

                <div class="text-center mt-4 pt-3 border-top">
                    <button type="button" id="btnBackToRegister" class="btn btn-sm btn-outline-secondary rounded-0">
                        <i class="fa-solid fa-arrow-left me-1"></i> Sửa lại thông tin đăng ký
                    </button>
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

    <script defer src="${pageContext.request.contextPath}/assets/js/auth/register.js"></script>
</body>
</html>
