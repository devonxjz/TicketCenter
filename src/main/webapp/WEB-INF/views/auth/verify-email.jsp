<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác minh Email - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-md-5">
            <div class="card shadow-sm border-0">
                <div class="card-body p-4">
                    <h3 class="fw-bold mb-3 text-center">Xác minh Email</h3>
                    <p class="text-muted small text-center mb-4">
                        Nhập địa chỉ email và mã xác thực OTP 6 chữ số để hoàn tất xác minh tài khoản.
                    </p>

                    <div id="alertBox" class="alert d-none" role="alert"></div>

                    <div class="mb-3">
                        <label class="form-label">Email tài khoản</label>
                        <div class="input-group">
                            <input type="email" id="emailInput" class="form-control" placeholder="name@example.com" required>
                            <button type="button" id="btnSendOtp" class="btn btn-outline-primary" onclick="handleSendOtp()">
                                Gửi OTP
                            </button>
                        </div>
                        <div class="form-text text-muted">
                            <span id="cooldownText"></span>
                        </div>
                    </div>

                    <div class="mb-3">
                        <label class="form-label">Mã OTP (6 chữ số)</label>
                        <input type="text" id="otpCodeInput" class="form-control text-center fs-4 letter-spacing-2" maxlength="6" placeholder="000000">
                    </div>

                    <button type="button" id="btnVerifyOtp" class="btn btn-primary w-100" onclick="handleVerifyOtp()">
                        Xác minh tài khoản
                    </button>

                    <div class="mt-4 text-center">
                        <a href="${pageContext.request.contextPath}/auth/login" class="small text-decoration-none">&larr; Quay lại đăng nhập</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
    const contextPath = '${pageContext.request.contextPath}';
    const csrfToken = '${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}';
    let cooldownTimer = null;
    let remainingCooldown = 0;

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
            showAlert('danger', 'Vui lòng nhập địa chỉ email.');
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
                showAlert('success', 'Xác minh email thành công! Đang chuyển hướng...');
                setTimeout(() => {
                    window.location.href = contextPath + '/auth/login';
                }, 1500);
            } else {
                showAlert('danger', (data.error && data.error.message) || 'Mã OTP không hợp lệ.');
            }
        } catch (e) {
            showAlert('danger', 'Lỗi kết nối khi xác thực.');
        } finally {
            btnVerify.disabled = false;
            btnVerify.innerText = 'Xác minh tài khoản';
        }
    }
</script>
</body>
</html>
