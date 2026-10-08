<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>Đăng ký tài khoản - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
</head>
<body class="bg-light">
<div class="container py-5">
    <div class="row justify-content-center">
        <div class="col-md-5">
            <div class="card shadow-sm border-0">
                <div class="card-body p-4">
                    <h3 class="fw-bold mb-3 text-center">Đăng ký tài khoản</h3>
                    <form action="${pageContext.request.contextPath}/api/auth/register" method="post">
                        <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
                        <div class="mb-3">
                            <label class="form-label">Tên tài khoản</label>
                            <input type="text" name="userName" class="form-control" required minlength="3" maxlength="100">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Họ và tên</label>
                            <input type="text" name="fullName" class="form-control" required maxlength="200">
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Email</label>
                            <input type="email" name="email" class="form-control" required>
                        </div>
                        <div class="mb-3">
                            <label class="form-label">Mật khẩu (ít nhất 12 ký tự)</label>
                            <input type="password" name="password" class="form-control" required minlength="12" maxlength="256">
                        </div>
                        <button type="submit" class="btn btn-primary w-100">Đăng ký</button>
                    </form>
                    <div class="mt-3 text-center">
                        <a href="${pageContext.request.contextPath}/WEB-INF/views/auth/login.jsp" class="small text-decoration-none">Đã có tài khoản? Đăng nhập</a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</div>
</body>
</html>
