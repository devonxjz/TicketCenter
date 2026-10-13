<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:url var="loginUrl" value="/auth/login"><c:if test="${not empty param.redirect}"><c:param name="redirect" value="${param.redirect}" /></c:if></c:url>
<c:url var="registerUrl" value="/auth/register"><c:if test="${not empty param.redirect}"><c:param name="redirect" value="${param.redirect}" /></c:if></c:url>
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
            <!-- Auth Switcher Tabs (Chỉ 2 tab Đăng nhập và Đăng ký) -->
            <div class="auth-tabs" id="authTabsHeader">
                <a href="${fn:escapeXml(loginUrl)}" class="auth-tab-btn">Đăng nhập</a>
                <a href="${fn:escapeXml(registerUrl)}" class="auth-tab-btn active">Đăng ký</a>
            </div>

            <!-- Bước 1: Form Đăng Ký -->
            <div id="formRegister">
                <h2 class="h4 fw-bold mb-1 text-dark">Tạo tài khoản mới</h2>
                <p class="small text-muted mb-4">Đăng ký tài khoản để mua vé nhanh chóng và quản lý đơn hàng.</p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="registerForm" action="${pageContext.request.contextPath}/api/auth/register" method="post">
                    <input type="hidden" name="_csrf" value="${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}">
                    <div class="mb-3">
                        <label class="filter-label" for="registerUserName">Tên đăng nhập duy nhất (userName) *</label>
                        <input id="registerUserName" name="userName" type="text" class="form-control rounded-0 border-secondary-subtle py-2" required minlength="3" maxlength="100" autocomplete="username" placeholder="nguyenvana" autofocus>
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerFullName">Họ và tên *</label>
                        <input id="registerFullName" name="fullName" type="text" class="form-control rounded-0 border-secondary-subtle py-2" required maxlength="200" placeholder="Nguyễn Văn A">
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerEmail">Địa chỉ Email *</label>
                        <input id="registerEmail" name="email" type="email" class="form-control rounded-0 border-secondary-subtle py-2" required placeholder="email@example.com">
                    </div>

                    <div class="mb-3">
                        <label class="filter-label" for="registerPassword">Mật khẩu bảo vệ (tối thiểu 12 ký tự) *</label>
                        <input id="registerPassword" name="password" type="password" class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12" maxlength="256" autocomplete="new-password" placeholder="Tối thiểu 12 ký tự">
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="registerPasswordConfirm">Xác nhận mật khẩu *</label>
                        <input id="registerPasswordConfirm" type="password" class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12" maxlength="256" autocomplete="new-password" placeholder="Nhập lại mật khẩu">
                    </div>

                    <button type="submit" id="btnRegister" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Đăng ký tài khoản
                    </button>

                    <div class="text-center mb-2">
                        <span class="small text-muted">Đã có tài khoản?</span>
                        <a href="${fn:escapeXml(loginUrl)}" class="small fw-bold text-dark text-decoration-underline ms-1">Đăng nhập</a>
                    </div>
                </form>
            </div>

            <!-- Bước 2: Nhập mã OTP trực tiếp tại chỗ sau khi đăng ký thành công -->
            <div id="formOtp" class="d-none">
                <div class="text-center mb-4">
                    <div class="d-inline-flex align-items-center justify-content-center bg-primary-subtle text-primary rounded-circle mb-3" style="width: 56px; height: 56px;">
                        <i class="fa-solid fa-envelope-circle-check fs-3"></i>
                    </div>
                    <h2 class="h4 fw-bold mb-1 text-dark">Xác thực tài khoản</h2>
                    <p class="small text-muted mb-1">Mã OTP kích hoạt đã được gửi đến email:</p>
                    <div class="fw-bold text-dark fs-6" id="displayRegisteredEmail"></div>
                    <p class="small text-muted mt-2">Vui lòng kiểm tra hộp thư (bao gồm cả thư mục Spam/Rác) và nhập mã 6 số bên dưới để hoàn tất.</p>
                </div>

                <div id="otpAlertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="otpVerifyForm">
                    <div class="mb-4">
                        <label class="filter-label text-center d-block mb-3">Nhập mã OTP 6 chữ số</label>
                        <div class="d-flex justify-content-center gap-2">
                            <input type="text" class="otp-input" maxlength="1" data-index="0" inputmode="numeric" autofocus>
                            <input type="text" class="otp-input" maxlength="1" data-index="1" inputmode="numeric">
                            <input type="text" class="otp-input" maxlength="1" data-index="2" inputmode="numeric">
                            <input type="text" class="otp-input" maxlength="1" data-index="3" inputmode="numeric">
                            <input type="text" class="otp-input" maxlength="1" data-index="4" inputmode="numeric">
                            <input type="text" class="otp-input" maxlength="1" data-index="5" inputmode="numeric">
                        </div>
                    </div>

                    <button type="submit" id="btnVerifyOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Xác nhận & Kích hoạt tài khoản
                    </button>
                </form>

                <div class="text-center mt-3">
                    <span id="cooldownText" class="small text-muted d-block mb-1"></span>
                    <button type="button" id="btnResendOtp" class="btn btn-link text-decoration-none small text-dark p-0 fw-semibold" disabled>
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

    <script>
        const contextPath = '${pageContext.request.contextPath}';
        let csrfToken = '${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}';
        let currentRegisteredEmail = '';
        let cooldownTimer = null;
        let remainingCooldown = 0;

        // Auto-advance logic for 6-digit OTP boxes
        const otpInputs = document.querySelectorAll('.otp-input');
        otpInputs.forEach((input, index) => {
            input.addEventListener('input', (e) => {
                if (e.target.value.length === 1 && index < otpInputs.length - 1) {
                    otpInputs[index + 1].focus();
                }
            });
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Backspace' && !e.target.value && index > 0) {
                    otpInputs[index - 1].focus();
                }
            });
            input.addEventListener('paste', (e) => {
                e.preventDefault();
                const text = (e.clipboardData || window.clipboardData).getData('text').trim();
                if (/^\d{6}$/.test(text)) {
                    text.split('').forEach((char, i) => {
                        if (otpInputs[i]) otpInputs[i].value = char;
                    });
                    otpInputs[5].focus();
                }
            });
        });

        function showOtpAlert(type, message) {
            const box = document.getElementById('otpAlertBox');
            box.className = 'alert alert-' + type;
            box.innerText = message;
            box.classList.remove('d-none');
        }

        function startCooldown(seconds) {
            remainingCooldown = seconds;
            const btnResend = document.getElementById('btnResendOtp');
            const cooldownText = document.getElementById('cooldownText');
            btnResend.disabled = true;

            if (cooldownTimer) clearInterval(cooldownTimer);
            cooldownTimer = setInterval(() => {
                if (remainingCooldown <= 0) {
                    clearInterval(cooldownTimer);
                    btnResend.disabled = false;
                    btnResend.innerText = 'Gửi lại mã OTP';
                    cooldownText.innerText = '';
                } else {
                    cooldownText.innerText = 'Có thể gửi lại sau ' + remainingCooldown + ' giây';
                    remainingCooldown--;
                }
            }, 1000);
        }

        // Bước 1: Xử lý submit Form Đăng Ký
        document.getElementById('registerForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            const alertBox = document.getElementById('alertBox');
            alertBox.classList.add('d-none');

            const userName = document.getElementById('registerUserName').value.trim();
            const fullName = document.getElementById('registerFullName').value.trim();
            const email = document.getElementById('registerEmail').value.trim();
            const password = document.getElementById('registerPassword').value;
            const confirmPassword = document.getElementById('registerPasswordConfirm').value;

            if (password !== confirmPassword) {
                alertBox.className = 'alert alert-danger';
                alertBox.innerText = 'Mật khẩu xác nhận không khớp.';
                alertBox.classList.remove('d-none');
                return;
            }

            if (password.length < 12) {
                alertBox.className = 'alert alert-danger';
                alertBox.innerText = 'Mật khẩu phải có độ dài từ 12 đến 256 ký tự.';
                alertBox.classList.remove('d-none');
                return;
            }

            const btn = document.getElementById('btnRegister');
            btn.disabled = true;
            btn.innerText = 'Đang xử lý đăng ký...';

            if (!csrfToken) {
                try {
                    const cResp = await fetch(contextPath + '/api/auth/csrf');
                    const cData = await cResp.json();
                    if (cData && cData.data && cData.data.token) {
                        csrfToken = cData.data.token;
                    }
                } catch (e) {}
            }

            try {
                const reqHeaders = { 'Content-Type': 'application/json' };
                if (csrfToken) {
                    reqHeaders['X-CSRF-Token'] = csrfToken;
                }
                const resp = await fetch(contextPath + '/api/auth/register', {
                    method: 'POST',
                    headers: reqHeaders,
                    body: JSON.stringify({
                        userName: userName,
                        fullName: fullName,
                        email: email,
                        password: password
                    })
                });
                const res = await resp.json();
                if (resp.ok && res.data && res.data.registered) {
                    currentRegisteredEmail = email;
                    document.getElementById('displayRegisteredEmail').innerText = email;

                    // Ẩn form đăng ký, hiển thị ngay ô nhập OTP tại chỗ
                    document.getElementById('formRegister').classList.add('d-none');
                    document.getElementById('formOtp').classList.remove('d-none');

                    // Khởi động đếm ngược gửi lại OTP và focus ô số đầu tiên
                    startCooldown(60);
                    otpInputs.forEach(i => i.value = '');
                    otpInputs[0].focus();
                } else {
                    alertBox.className = 'alert alert-danger';
                    alertBox.innerText = (res.error && res.error.message) || 'Đăng ký không thành công. Vui lòng kiểm tra lại thông tin.';
                    alertBox.classList.remove('d-none');
                    btn.disabled = false;
                    btn.innerText = 'Đăng ký tài khoản';
                }
            } catch (err) {
                alertBox.className = 'alert alert-danger';
                alertBox.innerText = 'Lỗi kết nối máy chủ. Vui lòng thử lại sau.';
                alertBox.classList.remove('d-none');
                btn.disabled = false;
                btn.innerText = 'Đăng ký tài khoản';
            }
        });

        // Bước 2: Xử lý submit Xác thực OTP tại chỗ
        document.getElementById('otpVerifyForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            const box = document.getElementById('otpAlertBox');
            box.classList.add('d-none');

            const code = Array.from(otpInputs).map(i => i.value.trim()).join('');
            if (code.length !== 6) {
                showOtpAlert('danger', 'Vui lòng nhập đủ 6 chữ số mã OTP.');
                return;
            }

            const btnVerify = document.getElementById('btnVerifyOtp');
            btnVerify.disabled = true;
            btnVerify.innerText = 'Đang kích hoạt tài khoản...';

            try {
                const resp = await fetch(contextPath + '/api/auth/otp/verify', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken
                    },
                    body: JSON.stringify({
                        email: currentRegisteredEmail,
                        purpose: 'VERIFY_EMAIL',
                        code: code
                    })
                });
                const res = await resp.json();
                if (resp.ok) {
                    showOtpAlert('success', 'Xác thực thành công! Đang chuyển đến trang đăng nhập...');
                    const urlParams = new URLSearchParams(window.location.search);
                    const redirect = urlParams.get('redirect');
                    const redirectQuery = redirect ? ('&redirect=' + encodeURIComponent(redirect)) : '';
                    setTimeout(() => {
                        window.location.href = contextPath + '/auth/login?registered=true' + redirectQuery;
                    }, 1200);
                } else {
                    showOtpAlert('danger', (res.error && res.error.message) || 'Mã OTP không hợp lệ hoặc đã hết hạn.');
                    btnVerify.disabled = false;
                    btnVerify.innerText = 'Xác nhận & Kích hoạt tài khoản';
                }
            } catch (err) {
                showOtpAlert('danger', 'Lỗi kết nối khi xác thực. Vui lòng thử lại.');
                btnVerify.disabled = false;
                btnVerify.innerText = 'Xác nhận & Kích hoạt tài khoản';
            }
        });

        // Gửi lại mã OTP
        document.getElementById('btnResendOtp').addEventListener('click', async function() {
            if (!currentRegisteredEmail) return;
            const btn = this;
            btn.disabled = true;
            try {
                const resp = await fetch(contextPath + '/api/auth/otp/send', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken
                    },
                    body: JSON.stringify({
                        email: currentRegisteredEmail,
                        purpose: 'VERIFY_EMAIL'
                    })
                });
                if (resp.ok) {
                    showOtpAlert('success', 'Đã gửi mã OTP mới đến email của bạn.');
                    startCooldown(60);
                } else {
                    const res = await resp.json();
                    showOtpAlert('danger', (res.error && res.error.message) || 'Không thể gửi lại mã OTP lúc này.');
                    btn.disabled = false;
                }
            } catch (err) {
                showOtpAlert('danger', 'Lỗi kết nối khi gửi lại OTP.');
                btn.disabled = false;
            }
        });

        // Quay lại sửa form đăng ký
        document.getElementById('btnBackToRegister').addEventListener('click', function() {
            document.getElementById('formOtp').classList.add('d-none');
            document.getElementById('formRegister').classList.remove('d-none');
            document.getElementById('btnRegister').disabled = false;
            document.getElementById('btnRegister').innerText = 'Đăng ký tài khoản';
        });
    </script>
</body>
</html>
