<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Nhật ký hệ thống"); request.setAttribute("financePage", "audit"); request.setAttribute("financeIntro", "Tra cứu các thao tác tài chính theo người thực hiện, đối tượng và thời gian. Chi tiết chỉ hiển thị thông tin được phép trong hệ thống."); %>
<%@ include file="../report/_header.jsp" %>
<section class="finance-panel"><div class="finance-panel-heading"><h2>Bộ lọc nhật ký</h2><span class="finance-tag">Chỉ quản trị viên</span></div>
<form id="filters" class="finance-filter-grid"><%@ include file="../report/_scope.jsp" %>
<label>Thao tác<select name="action"><option value="">Tất cả thao tác</option><option value="SETTLEMENT_CONFIRMED">Chốt quyết toán</option><option value="SETTLEMENT_RECALCULATED">Tính lại quyết toán</option><option value="REFUND_APPROVED">Duyệt hoàn tiền</option><option value="REFUND_REJECTED">Từ chối hoàn tiền</option><option value="EVENT_PUBLISHED">Công khai sự kiện</option></select></label>
<label>Đối tượng<select name="entityType"><option value="">Tất cả đối tượng</option><option value="Settlement">Quyết toán</option><option value="Event">Sự kiện</option><option value="Refund">Hoàn tiền</option><option value="Organization">Tổ chức</option></select></label>
<div class="finance-form-actions"><button type="submit">Lọc nhật ký</button></div></form></section>
<p id="basis"></p><section class="finance-panel"><div class="finance-panel-heading"><h2>Lịch sử thao tác</h2><span class="finance-tag">Mới nhất trước</span></div><div class="scroll" tabindex="0" aria-label="Bảng nhật ký"><table><caption class="finance-table-caption">Nhật ký mới nhất trước</caption><thead id="columns"></thead><tbody id="rows"></tbody></table></div>
<div class="finance-pagination" aria-label="Phân trang"><button id="previous" class="finance-button-secondary" type="button" disabled>&larr; Trang trước</button><span id="pagination" aria-live="polite"></span><button id="next" class="finance-button-secondary" type="button" disabled>Trang sau &rarr;</button></div></section>
<section id="audit-detail" class="finance-panel" aria-live="polite" aria-label="Chi tiết nhật ký"></section>
<%@ include file="../report/_footer.jsp" %>
