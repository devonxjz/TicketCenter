<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng nhập — TicketsCenter</title>
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
                <a href="${pageContext.request.contextPath}/auth/login${not empty param.redirect ? '?redirect='.concat(param.redirect) : ''}" class="auth-tab-btn active">Đăng nhập</a>
                <a href="${pageContext.request.contextPath}/auth/register${not empty param.redirect ? '?redirect='.concat(param.redirect) : ''}" class="auth-tab-btn">Đăng ký</a>
            </div>

            <!-- Login Form -->
            <div id="formLogin">
                <h2 class="h4 fw-bold mb-1 text-dark">Chào mừng trở lại</h2>
                <p class="small text-muted mb-4">Đăng nhập để theo dõi vé đã đặt và quản lý thông tin cá nhân.</p>

                <c:if test="${param.registered == 'true'}">
                    <div class="alert alert-success mb-3" role="alert">
                        <i class="fa-solid fa-circle-check me-2"></i>Kích hoạt tài khoản thành công! Vui lòng đăng nhập để tiếp tục.
                    </div>
                </c:if>

                <div id="alertBox" class="alert d-none mb-3" role="alert"></div>

                <form id="loginForm" action="${pageContext.request.contextPath}/api/auth/login" method="post">
                    <input type="hidden" name="_csrf" value="${csrfToken != null ? csrfToken : sessionScope.TC_CSRF_TOKEN}">
                    <div class="mb-3">
                        <label class="filter-label" for="identifier">Email hoặc Tên tài khoản</label>
                        <input type="text" id="identifier" name="identifier" class="form-control rounded-0 border-secondary-subtle py-2" required autofocus>
                    </div>

                    <div class="mb-4">
                        <div class="d-flex justify-content-between align-items-center">
                            <label class="filter-label" for="password">Mật khẩu</label>
                            <a href="${pageContext.request.contextPath}/auth/forgot-password" class="small text-danger text-decoration-none">Quên mật khẩu?</a>
                        </div>
                        <input type="password" id="password" name="password" class="form-control rounded-0 border-secondary-subtle py-2" required>
                    </div>

                    <button type="submit" id="btnLogin" class="btn-primary-custom w-100 py-3 mb-3 fs-6">
                        Đăng nhập
                    </button>

                    <div class="text-center mb-4">
                        <span class="small text-muted">Chưa có tài khoản?</span>
                        <a href="${pageContext.request.contextPath}/auth/register${not empty param.redirect ? '?redirect='.concat(param.redirect) : ''}" class="small fw-bold text-dark text-decoration-underline ms-1">Tạo tài khoản</a>
                    </div>
                </form>

                <c:if test="${showDemoAccounts}">
                <div class="p-3 bg-light border small text-muted">
                    <strong class="text-dark d-block mb-1">Tài khoản trải nghiệm mẫu (Email hoặc Username):</strong>
                    <div class="small">
                        • Admin: <code>admin</code> hoặc <code>adminTIcket@gmail.com</code> (pass: <code>123123ticketCenter</code>)<br>
                        • Quản lý BTC: <code>abcc_manager</code> hoặc <code>abcc@manager.com</code> (pass: <code>123</code>)<br>
                        • Soát vé: <code>abcc_staff</code> hoặc <code>abcc@staff.com</code> (pass: <code>123</code>)<br>
                        • Mua vé: <code>buyer1</code> hoặc <code>buyer@ticketscenter.vn</code> (pass: <code>123</code>)
                    </div>
                </div>
                </c:if>
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

        document.getElementById('loginForm').addEventListener('submit', async function(e) {
            e.preventDefault();
            const alertBox = document.getElementById('alertBox');
            alertBox.classList.add('d-none');
            const btn = document.getElementById('btnLogin');
            btn.disabled = true;
            btn.innerText = 'Đang đăng nhập...';

            const identifier = document.getElementById('identifier').value.trim();
            const password = document.getElementById('password').value;

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
                const resp = await fetch(contextPath + '/api/auth/login', {
                    method: 'POST',
                    headers: reqHeaders,
                    body: JSON.stringify({ identifier: identifier, password: password })
                });
                const res = await resp.json();
                if (resp.ok && res.data && res.data.authenticated) {
                    const redirectTarget = res.data?.redirect || (contextPath + '/');
                    window.location.href = redirectTarget;
                } else {
                    alertBox.className = 'alert alert-danger';
                    alertBox.innerText = (res.error && res.error.message) || 'Email hoặc mật khẩu không chính xác';
                    alertBox.classList.remove('d-none');
                    btn.disabled = false;
                    btn.innerText = 'Đăng nhập';
                }
            } catch (err) {
                document.getElementById('loginForm').submit();
            }
        });
    </script>
</body>
</html>
