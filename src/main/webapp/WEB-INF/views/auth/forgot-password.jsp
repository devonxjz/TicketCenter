<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quên mật khẩu - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-md-5">
            <div class="card shadow-sm border-0">
                <div class="card-body p-4">
                    <h3 class="fw-bold mb-3 text-center">Quên mật khẩu</h3>
                    <p class="text-muted small text-center mb-4">
                        Nhập địa chỉ email của bạn để nhận mã xác thực OTP (hiệu lực 5 phút) đặt lại mật khẩu.
                    </p>

                    <div id="alertBox" class="alert d-none" role="alert"></div>

                    <!-- Bước 1: Gửi OTP -->
                    <div id="stepSend">
                        <div class="mb-3">
                            <label class="form-label">Email tài khoản</label>
                            <input type="email" id="emailInput" class="form-control" placeholder="name@example.com" required autofocus>
                        </div>
                        <button type="button" id="btnSendOtp" class="btn btn-primary w-100" onclick="handleSendOtp()">
                            Gửi mã OTP
                        </button>
                    </div>

                    <!-- Bước 2: Nhập OTP -->
                    <div id="stepVerify" class="d-none mt-4">
                        <div class="mb-3">
                            <label class="form-label">Mã OTP (6 chữ số)</label>
                            <input type="text" id="otpCodeInput" class="form-control text-center fs-4 letter-spacing-2" maxlength="6" placeholder="000000">
                            <div class="form-text text-muted">
                                Không nhận được mã? <span id="cooldownText" class="text-secondary"></span>
                                <button type="button" id="btnResendOtp" class="btn btn-link btn-sm p-0 d-none" onclick="handleSendOtp()">Gửi lại mã</button>
                            </div>
                        </div>
                        <button type="button" id="btnVerifyOtp" class="btn btn-success w-100" onclick="handleVerifyOtp()">
                            Xác thực mã OTP
                        </button>
                    </div>

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
        const resendBtn = document.getElementById('btnResendOtp');
        const cooldownText = document.getElementById('cooldownText');
        resendBtn.classList.add('d-none');

        if (cooldownTimer) clearInterval(cooldownTimer);
        cooldownTimer = setInterval(() => {
            if (remainingCooldown <= 0) {
                clearInterval(cooldownTimer);
                cooldownText.innerText = '';
                resendBtn.classList.remove('d-none');
            } else {
                cooldownText.innerText = 'Gửi lại sau (' + remainingCooldown + 's)';
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

        const btnSend = document.getElementById('btnSendOtp');
        btnSend.disabled = true;
        btnSend.innerText = 'Đang gửi...';

        try {
            const resp = await fetch(contextPath + '/api/auth/otp/send', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfToken
                },
                body: JSON.stringify({ email: email, purpose: 'RESET_PASSWORD' })
            });
            const data = await resp.json();

            if (resp.ok) {
                showAlert('success', 'Mã OTP đã được gửi đến email (nếu tài khoản tồn tại). Vui lòng kiểm tra hộp thư.');
                document.getElementById('stepVerify').classList.remove('d-none');
                startCooldown(60);
            } else {
                showAlert('danger', (data.error && data.error.message) || 'Không thể gửi mã OTP.');
            }
        } catch (e) {
            showAlert('danger', 'Lỗi kết nối máy chủ, vui lòng thử lại.');
        } finally {
            btnSend.disabled = false;
            btnSend.innerText = 'Gửi mã OTP';
        }
    }

    async function handleVerifyOtp() {
        const email = document.getElementById('emailInput').value.trim();
        const code = document.getElementById('otpCodeInput').value.trim();
        if (!code || code.length !== 6) {
            showAlert('danger', 'Vui lòng nhập đúng 6 chữ số OTP.');
            return;
        }

        const btnVerify = document.getElementById('btnVerifyOtp');
        btnVerify.disabled = true;
        btnVerify.innerText = 'Đang xác thực...';

        try {
            const resp = await fetch(contextPath + '/api/auth/otp/verify', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'X-CSRF-Token': csrfToken
                },
                body: JSON.stringify({ email: email, purpose: 'RESET_PASSWORD', code: code })
            });
            const res = await resp.json();

            if (resp.ok && res.data && res.data.resetToken) {
                showAlert('success', 'Xác thực OTP thành công! Đang chuyển hướng đặt lại mật khẩu...');
                setTimeout(() => {
                    window.location.href = contextPath + '/auth/reset-password?token=' + encodeURIComponent(res.data.resetToken);
                }, 1000);
            } else {
                showAlert('danger', (res.error && res.error.message) || 'Mã OTP không hợp lệ hoặc đã hết hạn.');
            }
        } catch (e) {
            showAlert('danger', 'Lỗi kết nối khi xác thực OTP.');
        } finally {
            btnVerify.disabled = false;
            btnVerify.innerText = 'Xác thực mã OTP';
        }
    }
</script>
</body>
</html>
