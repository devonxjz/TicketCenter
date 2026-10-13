<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu — TicketsCenter</title>

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
                <a href="${pageContext.request.contextPath}/auth/login" class="auth-tab-btn"><i class="fa-solid fa-arrow-left me-1"></i> Đăng nhập</a>
                <span class="auth-tab-btn active">Đặt lại mật khẩu</span>
            </div>

<%
    String rawToken = (String) request.getAttribute("token");
    String safeToken = "";
    if (rawToken != null && rawToken.matches("^[A-Za-z0-9_.-]+$")) {
        safeToken = rawToken;
    }
%>

            <!-- Reset Password Content -->
            <div>
                <h2 class="h4 fw-bold mb-1 text-dark">Thiết lập mật khẩu mới</h2>
                <p class="small text-muted mb-4">
                    Nhập mật khẩu mới của bạn (tối thiểu 12 ký tự) để hoàn tất việc khôi phục quyền truy cập.
                </p>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="resetForm" onsubmit="handleResetPassword(event)">
                    <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                    <input type="hidden" id="tokenInput" value="<%= safeToken %>">

                    <div class="mb-3">
                        <label class="filter-label" for="newPassword">Mật khẩu mới (tối thiểu 12 ký tự) *</label>
                        <input type="password" id="newPassword" class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12" maxlength="256" placeholder="Ít nhất 12 ký tự" autofocus>
                    </div>

                    <div class="mb-4">
                        <label class="filter-label" for="confirmPassword">Xác nhận mật khẩu mới *</label>
                        <input type="password" id="confirmPassword" class="form-control rounded-0 border-secondary-subtle py-2" required minlength="12" maxlength="256" placeholder="Nhập lại mật khẩu mới">
                    </div>

                    <button type="submit" id="btnSubmit" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Cập nhật mật khẩu mới <i class="fa-solid fa-key ms-1"></i>
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
                btnSubmit.innerText = 'Cập nhật mật khẩu mới';
            }
        }
    </script>
</body>
</html>
