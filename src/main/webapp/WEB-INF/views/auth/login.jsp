<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>Đăng nhập - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-md-5">
            <div class="card shadow-sm border-0">
                <div class="card-body p-4">
                    <h3 class="fw-bold mb-3 text-center">Đăng nhập</h3>
                    <form action="${pageContext.request.contextPath}/api/auth/login" method="post">
                        <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                        <div class="mb-3">
                            <label class="form-label">Email hoặc Tên tài khoản</label>
                            <input type="text" name="identifier" class="form-control" required autofocus>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Mật khẩu</label>
                            <input type="password" name="password" class="form-control" required>
                        </div>
                        <button type="submit" class="btn btn-primary w-100">Đăng nhập</button>
                    </form>
                    <div class="mt-3 d-flex justify-content-between">
                        <a href="${pageContext.request.contextPath}/auth/forgot-password" class="small text-decoration-none">Quên mật khẩu?</a>
                        <a href="${pageContext.request.contextPath}/auth/register" class="small text-decoration-none">Đăng ký tài khoản</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>
