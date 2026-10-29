// Page script for auth/register.jsp; reads the context path and CSRF token from the layout meta tags.
const contextPath = document.querySelector('meta[name="context-path"]').content;
let csrfToken = document.querySelector('meta[name="csrf-token"]').content;
let currentRegisteredEmail = '';
let cooldownTimer = null;
let remainingCooldown = 0;
// Một ô nhập duy nhất: chỉ giữ chữ số (kể cả khi dán mã có khoảng trắng)
const otpInput = document.getElementById('otpCodeInput');
otpInput.addEventListener('input', () => {
    otpInput.value = otpInput.value.replace(/\D/g, '').slice(0, 6);
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
        const reqHeaders = {
            'Content-Type': 'application/json'
        };
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
            otpInput.value = '';
            otpInput.focus();
            if (res.data.otpSent === false) {
                // Tài khoản đã tạo nhưng mail chưa gửi được: cho phép gửi lại ngay
                showOtpAlert('warning',
                    'Tài khoản đã được tạo nhưng chưa gửi được mã OTP. Vui lòng bấm "Gửi lại mã OTP".');
                document.getElementById('btnResendOtp').disabled = false;
            } else {
                document.getElementById('otpAlertBox').classList.add('d-none');
                startCooldown(60);
            }
        } else {
            alertBox.className = 'alert alert-danger';
            alertBox.innerText = (res.error && res.error.message) ||
                'Đăng ký không thành công. Vui lòng kiểm tra lại thông tin.';
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
    const code = otpInput.value.trim();
    if (!/^\d{6}$/.test(code)) {
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
            showOtpAlert('danger', (res.error && res.error.message) ||
                'Mã OTP không hợp lệ hoặc đã hết hạn.');
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
