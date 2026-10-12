<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html><html lang="vi"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Yêu cầu hoàn tiền · TicketCenter</title>
<link rel="stylesheet" href="<c:url value='/assets/refund/admin-refunds.css'/>">
<script defer src="<c:url value='/assets/refund/admin-refunds.js'/>"></script></head>
<body data-page="list" data-context="<c:out value='${pageContext.request.contextPath}'/>">
<main><header><a href="<c:url value='/admin'/>">TicketCenter · Quản trị</a><h1>Yêu cầu hoàn tiền</h1><p>Duyệt hồ sơ và theo dõi từng lần chuyển tiền về phương thức thanh toán gốc.</p></header>
<form id="filter"><label for="status">Trạng thái</label><select name="status" id="status">
<option value="">Tất cả</option><option value="REQUESTED">Chờ duyệt</option><option value="APPROVED">Đã duyệt</option><option value="PROCESSING">Đang xử lý</option><option value="COMPLETED">Hoàn tất</option><option value="REJECTED">Đã từ chối</option><option value="RETRYABLE">Thất bại</option><option value="NEEDS_RECONCILIATION">Cần đối soát</option>
</select><label for="pageSize">Số hồ sơ mỗi trang</label><select id="pageSize" name="pageSize"><option>20</option><option>50</option><option>100</option></select><button>Lọc hồ sơ</button></form>
<p id="feedback" role="status" aria-live="polite">Đang tải hồ sơ…</p><button id="reload" type="button" hidden>Tải lại</button>
<div class="table-wrap"><table><caption class="sr-only">Danh sách yêu cầu hoàn tiền</caption><thead><tr><th>Mã yêu cầu / Đơn hàng</th><th>Người mua</th><th>Sự kiện</th><th>Số vé</th><th>Số tiền</th><th>Trạng thái</th><th>Ngày yêu cầu</th><th></th></tr></thead><tbody id="rows"></tbody></table></div>
<nav class="pager" aria-label="Phân trang"><button id="prev" type="button">Trang trước</button><span id="pagination"></span><button id="next" type="button">Trang sau</button></nav>
</main></body></html>
