// Page script for auth/forgot-password.jsp; reads the context path and CSRF token from the layout meta tags.
const contextPath = document.querySelector('meta[name="context-path"]').content;
const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
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
            body: JSON.stringify({
                email: email,
                purpose: 'RESET_PASSWORD'
            })
        });
        const data = await resp.json();
        if (resp.ok) {
            showAlert('success',
            'Mã OTP đã được gửi đến email (nếu tài khoản tồn tại). Vui lòng kiểm tra hộp thư.');
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
            body: JSON.stringify({
                email: email,
                purpose: 'RESET_PASSWORD',
                code: code
            })
        });
        const res = await resp.json();
        if (resp.ok && res.data && res.data.resetToken) {
            showAlert('success', 'Xác thực OTP thành công! Đang chuyển hướng đặt lại mật khẩu...');
            setTimeout(() => {
                window.location.href = contextPath + '/auth/reset-password?token=' + encodeURIComponent(res
                    .data.resetToken);
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
