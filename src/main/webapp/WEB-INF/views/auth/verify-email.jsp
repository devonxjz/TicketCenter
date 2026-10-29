<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác minh Email — TicketsCenter</title>

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
                <a href="${pageContext.request.contextPath}/auth/login" class="auth-tab-btn">Đăng nhập</a>
                <a href="${pageContext.request.contextPath}/auth/register" class="auth-tab-btn">Đăng ký</a>
                <a href="${pageContext.request.contextPath}/auth/verify-email" class="auth-tab-btn active">Xác thực
                    OTP</a>
            </div>

            <!-- Verify Email Form -->
            <div>
                <h2 class="h4 fw-bold mb-1 text-dark">Xác minh tài khoản</h2>
                <p class="small text-muted mb-4">
                    Nhập địa chỉ email và mã xác thực OTP 6 chữ số để hoàn tất xác minh tài khoản của bạn.
                </p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="verifyEmailForm" onsubmit="event.preventDefault(); handleVerifyOtp();">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">

                    <div class="mb-3">
                        <label class="filter-label" for="emailInput">Email tài khoản *</label>
                        <div class="input-group">
                            <input type="email" id="emailInput"
                                class="form-control rounded-0 border-secondary-subtle py-2"
                                placeholder="name@example.com" required autofocus>
                            <button type="button" id="btnSendOtp" class="btn btn-dark rounded-0 px-3 fw-bold"
                                onclick="handleSendOtp()">
                                Gửi OTP
                            </button>
                        </div>
                        <div class="form-text text-muted mt-2">
                            <span id="cooldownText"></span>
                        </div>
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="otpCodeInput">Mã OTP (6 chữ số) *</label>
                        <input type="text" id="otpCodeInput"
                            class="form-control rounded-0 border-secondary-subtle py-2 otp-input" maxlength="6"
                            placeholder="000000" required>
                        <span class="small text-muted mt-1 d-block">Mã OTP có hiệu lực 5 phút; nhập sai quá 5 lần sẽ bị
                            hủy.</span>
                    </div>

                    <button type="submit" id="btnVerifyOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Xác nhận & Hoàn tất <i class="fa-solid fa-check ms-1"></i>
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

    <script defer src="${pageContext.request.contextPath}/assets/js/auth/verify-email.js"></script>
</body>
</html>
