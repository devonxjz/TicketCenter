// Page script for auth/reset-password.jsp; reads the context path and CSRF token from the layout meta tags.
const contextPath = document.querySelector('meta[name="context-path"]').content;
const csrfToken = document.querySelector('meta[name="csrf-token"]').content;

function showAlert(type, message) {
    const alertBox = document.getElementById('alertBox');
    alertBox.className = 'alert alert-' + type;
    alertBox.innerText = message;
    alertBox.classList.remove('d-none');
}
async function handleResetPassword(e) {
    e.preventDefault();
    // token from the hidden input or the query string
    let token = document.getElementById('tokenInput').value;
    if (!token) {
        const urlParams = new URLSearchParams(window.location.search);
        token = urlParams.get('token');
    }
    if (!token || !/^[A-Za-z0-9_.-]+$/.test(token)) {
        showAlert('danger',
            'Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thực hiện lại từ bước quên mật khẩu.'
            );
        return;
    }
    const newPassword = document.getElementById('newPassword').value;
    const confirmPassword = document.getElementById('confirmPassword').value;
    if (newPassword.length < 12) {
        showAlert('danger', 'Mật khẩu phải có ít nhất 12 ký tự.');
        return;
    }
    if (newPassword !== confirmPassword) {
        showAlert('danger', 'Mật khẩu xác nhận không khớp.');
        return;
    }
    const btnSubmit = document.getElementById('btnSubmit');
    btnSubmit.disabled = true;
    btnSubmit.innerText = 'Đang xử lý...';
    try {
        const resp = await fetch(contextPath + '/api/auth/password/reset', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'X-CSRF-Token': csrfToken
            },
            body: JSON.stringify({
                token: token,
                newPassword: newPassword
            })
        });
        const data = await resp.json();
        if (resp.ok && data.data && data.data.reset) {
            showAlert('success', 'Đặt lại mật khẩu thành công! Đang chuyển về trang đăng nhập...');
            setTimeout(() => {
                window.location.href = contextPath + '/auth/login';
            }, 1500);
        } else {
            showAlert('danger', (data.error && data.error.message) ||
                'Không thể đặt lại mật khẩu. Token có thể đã hết hạn.');
        }
    } catch (err) {
        showAlert('danger', 'Lỗi kết nối khi gửi yêu cầu đặt lại mật khẩu.');
    } finally {
        btnSubmit.disabled = false;
        btnSubmit.innerText = 'Cập nhật mật khẩu mới';
    }
}
