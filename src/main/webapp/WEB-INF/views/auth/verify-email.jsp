<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác minh Email — TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css">
    <style>
        .auth-container {
            max-width: 480px;
            margin: 60px auto;
            border: 1px solid var(--gray-border);
            padding: 40px;
            background: var(--white);
        }
        .auth-tabs {
            display: flex;
            border-bottom: 1px solid var(--gray-border);
            margin-bottom: 32px;
        }
        .auth-tab-btn {
            flex: 1;
            text-align: center;
            padding: 12px 0;
            font-size: 0.95rem;
            font-weight: 700;
            background: transparent;
            border: none;
            border-bottom: 2px solid transparent;
            color: var(--gray-text);
            cursor: pointer;
            transition: all 0.15s ease;
            text-decoration: none;
        }
        .auth-tab-btn.active {
            color: var(--black);
            border-bottom-color: var(--primary);
        }
        .auth-tab-btn:hover {
            color: var(--black);
        }
        .otp-input {
            letter-spacing: 0.3em;
            font-size: 1.5rem;
            font-weight: 700;
            text-align: center;
        }
    </style>
</head>
<body class="bg-light min-h-screen d-flex flex-column justify-content-between">
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
                <a href="${pageContext.request.contextPath}/auth/verify-email" class="auth-tab-btn active">Xác thực OTP</a>
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
                            <input type="email" id="emailInput" class="form-control rounded-0 border-secondary-subtle py-2" placeholder="name@example.com" required autofocus>
                            <button type="button" id="btnSendOtp" class="btn btn-dark rounded-0 px-3 fw-bold" onclick="handleSendOtp()">
                                Gửi OTP
                            </button>
                        </div>
                        <div class="form-text text-muted mt-2">
                            <span id="cooldownText"></span>
                        </div>
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="otpCodeInput">Mã OTP (6 chữ số) *</label>
                        <input type="text" id="otpCodeInput" class="form-control rounded-0 border-secondary-subtle py-2 otp-input" maxlength="6" placeholder="000000" required>
                        <span class="small text-muted mt-1 d-block">Mã OTP có hiệu lực 5 phút; nhập sai quá 5 lần sẽ bị hủy.</span>
                    </div>

                    <button type="submit" id="btnVerifyOtp" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Xác nhận & Hoàn tất <i class="fa-solid fa-check ms-1"></i>
                    </button>
                </form>

                <div class="mt-4 text-center">
                    <a href="${pageContext.request.contextPath}/auth/login" class="small text-decoration-none text-muted">&larr; Quay lại đăng nhập</a>
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
        const csrfToken = '${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}';
        let cooldownTimer = null;
        let remainingCooldown = 0;

        // Tự động điền email nếu có trên query URL
        document.addEventListener('DOMContentLoaded', () => {
            const urlParams = new URLSearchParams(window.location.search);
            const emailParam = urlParams.get('email');
            if (emailParam) {
                document.getElementById('emailInput').value = emailParam;
            }
        });

        function showAlert(type, message) {
            const alertBox = document.getElementById('alertBox');
            alertBox.className = 'alert alert-' + type;
            alertBox.innerText = message;
            alertBox.classList.remove('d-none');
        }

        function startCooldown(seconds) {
            remainingCooldown = seconds;
            const btnSend = document.getElementById('btnSendOtp');
            const cooldownText = document.getElementById('cooldownText');
            btnSend.disabled = true;

            if (cooldownTimer) clearInterval(cooldownTimer);
            cooldownTimer = setInterval(() => {
                if (remainingCooldown <= 0) {
                    clearInterval(cooldownTimer);
                    btnSend.disabled = false;
                    btnSend.innerText = 'Gửi lại OTP';
                    cooldownText.innerText = '';
                } else {
                    cooldownText.innerText = 'Có thể gửi lại sau ' + remainingCooldown + ' giây';
                    remainingCooldown--;
                }
            }, 1000);
        }

        async function handleSendOtp() {
            const email = document.getElementById('emailInput').value.trim();
            if (!email) {
                showAlert('danger', 'Vui lòng nhập địa chỉ email hợp lệ.');
                return;
            }

            try {
                const resp = await fetch(contextPath + '/api/auth/otp/send', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken
                    },
                    body: JSON.stringify({ email: email, purpose: 'VERIFY_EMAIL' })
                });
                const data = await resp.json();

                if (resp.ok) {
                    showAlert('success', 'Mã xác thực OTP đã được gửi đến email của bạn.');
                    startCooldown(60);
                } else {
                    showAlert('danger', (data.error && data.error.message) || 'Không thể gửi mã OTP.');
                }
            } catch (e) {
                showAlert('danger', 'Lỗi kết nối khi gửi mã OTP.');
            }
        }

        async function handleVerifyOtp() {
            const email = document.getElementById('emailInput').value.trim();
            const code = document.getElementById('otpCodeInput').value.trim();

            if (!email || !code || code.length !== 6) {
                showAlert('danger', 'Vui lòng nhập đầy đủ email và 6 chữ số OTP.');
                return;
            }

            const btnVerify = document.getElementById('btnVerifyOtp');
            btnVerify.disabled = true;
            btnVerify.innerText = 'Đang xác minh...';

            try {
                const resp = await fetch(contextPath + '/api/auth/otp/verify', {
                    method: 'POST',
                    headers: { 
                        'Content-Type': 'application/json',
                        'X-CSRF-Token': csrfToken
                    },
                    body: JSON.stringify({ email: email, purpose: 'VERIFY_EMAIL', code: code })
                });
                const data = await resp.json();

                if (resp.ok) {
                    showAlert('success', 'Xác minh email thành công! Đang chuyển hướng đến đăng nhập...');
                    const urlParams = new URLSearchParams(window.location.search);
                    const redirect = urlParams.get('redirect');
                    const redirectQuery = redirect ? ('?redirect=' + encodeURIComponent(redirect)) : '';
                    setTimeout(() => {
                        window.location.href = contextPath + '/auth/login' + redirectQuery;
                    }, 1500);
                } else {
                    showAlert('danger', (data.error && data.error.message) || 'Mã OTP không hợp lệ.');
                }
            } catch (e) {
                showAlert('danger', 'Lỗi kết nối khi xác thực.');
            } finally {
                btnVerify.disabled = false;
                btnVerify.innerText = 'Xác nhận & Hoàn tất';
            }
        }
    </script>
</body>
</html>
