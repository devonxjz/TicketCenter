<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quên mật khẩu — TicketsCenter</title>

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
                <span class="auth-tab-btn active">Quên mật khẩu</span>
            </div>

            <!-- Forgot Password Content -->
            <div>
                <h2 class="h4 fw-bold mb-1 text-dark">Khôi phục mật khẩu</h2>
                <p class="small text-muted mb-4">
                    Nhập địa chỉ email của bạn để nhận mã xác thực OTP (hiệu lực 5 phút) đặt lại mật khẩu.
                </p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="forgotPasswordForm">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">

                    <!-- Bước 1: Gửi OTP -->
                    <div id="stepSend">
                        <div class="mb-3">
                            <label class="filter-label" for="emailInput">Email tài khoản</label>
                            <input type="email" id="emailInput"
                                class="form-control rounded-0 border-secondary-subtle py-2"
                                placeholder="name@example.com" required autofocus>
                        </div>
                        <button type="button" id="btnSendOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6"
                            onclick="handleSendOtp()">
                            Gửi mã OTP
                        </button>
                    </div>

                    <!-- Bước 2: Nhập OTP -->
                    <div id="stepVerify" class="d-none mt-4">
                        <div class="mb-3">
                            <label class="filter-label" for="otpCodeInput">Mã OTP (6 chữ số)</label>
                            <input type="text" id="otpCodeInput"
                                class="form-control rounded-0 border-secondary-subtle py-2 otp-input" maxlength="6"
                                placeholder="000000">
                            <div class="form-text text-muted mt-2">
                                Không nhận được mã? <span id="cooldownText" class="text-secondary"></span>
                                <button type="button" id="btnResendOtp"
                                    class="btn btn-link btn-sm p-0 text-danger text-decoration-none d-none"
                                    onclick="handleSendOtp()">Gửi lại mã</button>
                            </div>
                        </div>
                        <button type="button" id="btnVerifyOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6"
                            onclick="handleVerifyOtp()">
                            Xác thực mã OTP <i class="fa-solid fa-check ms-1"></i>
                        </button>
                    </div>
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

    <script defer src="${pageContext.request.contextPath}/assets/js/auth/forgot-password.js"></script>
</body>
</html>
