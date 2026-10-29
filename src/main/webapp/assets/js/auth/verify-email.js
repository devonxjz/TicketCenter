// Page script for auth/verify-email.jsp; reads the context path and CSRF token from the layout meta tags.
const contextPath = document.querySelector('meta[name="context-path"]').content;
const csrfToken = document.querySelector('meta[name="csrf-token"]').content;
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
            body: JSON.stringify({
                email: email,
                purpose: 'VERIFY_EMAIL'
            })
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
            body: JSON.stringify({
                email: email,
                purpose: 'VERIFY_EMAIL',
                code: code
            })
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
