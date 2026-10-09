<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-md-5">
            <div class="card shadow-sm border-0">
                <div class="card-body p-4">
                    <h3 class="fw-bold mb-3 text-center">Đặt lại mật khẩu</h3>
                    <p class="text-muted small text-center mb-4">
                        Nhập mật khẩu mới của bạn (tối thiểu 12 ký tự).
                    </p>

<%
    String rawToken = (String) request.getAttribute("token");
    String safeToken = "";
    if (rawToken != null && rawToken.matches("^[A-Za-z0-9_.-]+$")) {
        safeToken = rawToken;
    }
%>

                    <div id="alertBox" class="alert d-none" role="alert"></div>

                    <form id="resetForm" onsubmit="handleResetPassword(event)">
                        <input type="hidden" id="tokenInput" value="<%= safeToken %>">

                        <div class="mb-3">
                            <label class="form-label">Mật khẩu mới</label>
                            <input type="password" id="newPassword" class="form-control" required minlength="12" maxlength="256" placeholder="Ít nhất 12 ký tự" autofocus>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Xác nhận mật khẩu mới</label>
                            <input type="password" id="confirmPassword" class="form-control" required minlength="12" maxlength="256" placeholder="Nhập lại mật khẩu mới">
                        </div>
                        <button type="submit" id="btnSubmit" class="btn btn-primary w-100">
                            Cập nhật mật khẩu
                        </button>
                    </form>

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

    function showAlert(type, message) {
        const alertBox = document.getElementById('alertBox');
        alertBox.className = 'alert alert-' + type;
        alertBox.innerText = message;
        alertBox.classList.remove('d-none');
    }

    async function handleResetPassword(e) {
        e.preventDefault();

        // Lấy token từ input ẩn hoặc query param
        let token = document.getElementById('tokenInput').value;
        if (!token) {
            const urlParams = new URLSearchParams(window.location.search);
            token = urlParams.get('token');
        }

        if (!token || !/^[A-Za-z0-9_.-]+$/.test(token)) {
            showAlert('danger', 'Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn. Vui lòng thực hiện lại từ bước quên mật khẩu.');
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
                body: JSON.stringify({ token: token, newPassword: newPassword })
            });
            const data = await resp.json();

            if (resp.ok && data.data && data.data.reset) {
                showAlert('success', 'Đặt lại mật khẩu thành công! Đang chuyển về trang đăng nhập...');
                setTimeout(() => {
                    window.location.href = contextPath + '/auth/login';
                }, 1500);
            } else {
                showAlert('danger', (data.error && data.error.message) || 'Không thể đặt lại mật khẩu. Token có thể đã hết hạn.');
            }
        } catch (err) {
            showAlert('danger', 'Lỗi kết nối khi gửi yêu cầu đặt lại mật khẩu.');
        } finally {
            btnSubmit.disabled = false;
            btnSubmit.innerText = 'Cập nhật mật khẩu';
        }
    }
</script>
</body>
</html>
