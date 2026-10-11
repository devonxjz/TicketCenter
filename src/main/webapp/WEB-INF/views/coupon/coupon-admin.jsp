<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản Lý Mã Giảm Giá - TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.1/css/all.min.css">
    <style>
        body {
            background-color: #f8fafc;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
            color: #1e293b;
        }
        .admin-card {
            background: #ffffff;
            border-radius: 14px;
            border: 1px solid #e2e8f0;
            padding: 24px;
        }
    </style>
</head>
<body>

<div class="container py-5">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <div>
            <h2 class="fw-bold mb-1"><i class="fa-solid fa-tags text-primary me-2"></i>Quản Trị Mã Giảm Giá (Coupon)</h2>
            <p class="text-muted mb-0">Quản lý các chương trình ưu đãi, giới hạn sử dụng và tỷ lệ giảm giá theo ban tổ chức</p>
        </div>
        <a href="${pageContext.request.contextPath}/" class="btn btn-outline-secondary">
            <i class="fa-solid fa-house me-1"></i> Trang chủ
        </a>
    </div>

    <div class="admin-card mb-4">
        <div class="d-flex justify-content-between align-items-center mb-3">
            <h5 class="fw-bold mb-0">Danh Sách Mã Giảm Giá</h5>
            <span class="badge bg-primary-subtle text-primary px-3 py-2">UI-14 Quản trị Coupon</span>
        </div>

        <c:choose>
            <c:when test="${empty coupons}">
                <div class="text-center py-5">
                    <i class="fa-regular fa-folder-open text-muted mb-3" style="font-size: 48px;"></i>
                    <p class="text-muted">Chưa có mã giảm giá nào được tạo cho tổ chức này.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="table table-hover align-middle">
                        <thead class="table-light">
                            <tr>
                                <th>Mã Code</th>
                                <th>Loại giảm giá</th>
                                <th>Giá trị</th>
                                <th class="text-center">Đã dùng / Tổng</th>
                                <th class="text-center">Còn lại</th>
                                <th>Thời hạn</th>
                                <th class="text-center">Trạng thái</th>
                            </tr>
                        </thead>
                        <tbody>
                            <c:forEach var="c" items="${coupons}">
                                <tr>
                                    <td>
                                        <span class="badge bg-light text-dark font-monospace border px-2 py-1 fs-6">${c.code}</span>
                                    </td>
                                    <td>
                                        <c:choose>
                                            <c:when test="${c.discountType == 'PERCENTAGE'}">Phần trăm (%)</c:when>
                                            <c:otherwise>Số tiền cố định</c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td class="fw-bold text-primary">
                                        <c:choose>
                                            <c:when test="${c.discountType == 'PERCENTAGE'}">
                                                ${c.percentageValue}% (Tối đa 30%)
                                            </c:when>
                                            <c:otherwise>
                                                <fmt:formatNumber value="${c.fixedAmount}" type="currency" currencySymbol="₫" maxFractionDigits="0"/>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                    <td class="text-center">
                                        <span class="fw-bold">${c.consumedUses}</span> / ${c.maxUses}
                                    </td>
                                    <td class="text-center">
                                        <span class="badge bg-info-subtle text-info">${c.availableUses}</span>
                                    </td>
                                    <td class="small text-muted">
                                        ${c.validFrom} <br>đến ${c.validTo}
                                    </td>
                                    <td class="text-center">
                                        <c:choose>
                                            <c:when test="${c.active}">
                                                <span class="badge bg-success-subtle text-success px-3 py-1 rounded-pill">Hoạt động</span>
                                            </c:when>
                                            <c:otherwise>
                                                <span class="badge bg-secondary-subtle text-secondary px-3 py-1 rounded-pill">Tạm dừng</span>
                                            </c:otherwise>
                                        </c:choose>
                                    </td>
                                </tr>
                            </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
