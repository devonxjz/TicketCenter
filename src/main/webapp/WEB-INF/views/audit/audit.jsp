<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Nhật ký hệ thống"); request.setAttribute("financePage", "audit"); request.setAttribute("financeIntro", "Tra cứu các thao tác tài chính theo người thực hiện, đối tượng và thời gian. Chi tiết chỉ hiển thị thông tin được phép trong hệ thống."); %>
<%@ include file="../report/_header.jsp" %>
<section class="finance-panel"><div class="finance-panel-heading"><h2>Bộ lọc nhật ký</h2><span class="finance-tag">Chỉ quản trị viên</span></div>
<form id="filters" class="finance-filter-grid"><%@ include file="../report/_scope.jsp" %>
<label>Thao tác<input name="action" maxlength="100" pattern="[A-Z][A-Z0-9_]{0,99}" placeholder="Ví dụ: SETTLEMENT_CONFIRMED"></label>
<label>Loại đối tượng<input name="entityType" maxlength="100" pattern="[A-Za-z][A-Za-z0-9_]{0,99}" placeholder="Ví dụ: Settlement"></label>
<label>Bảng đối tượng<input name="targetTable" maxlength="100" pattern="(dbo\.)?tc_[A-Za-z0-9_]+" placeholder="Ví dụ: tc_settlements"></label>
<label>Mã đối tượng<input name="entityId" maxlength="100" pattern="[A-Za-z0-9_-]{1,100}"></label>
<label>Mã người thực hiện (UUID)<input name="actorId" maxlength="36" pattern="[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}"></label>
<div class="finance-form-actions"><button type="submit">Lọc nhật ký</button></div></form></section>
<p id="basis"></p><section class="finance-panel"><div class="finance-panel-heading"><h2>Lịch sử thao tác</h2><span class="finance-tag">Mới nhất trước</span></div><div class="scroll" tabindex="0" aria-label="Bảng nhật ký"><table><caption class="finance-table-caption">Nhật ký mới nhất trước</caption><thead id="columns"></thead><tbody id="rows"></tbody></table></div>
<div class="finance-pagination" aria-label="Phân trang"><button id="previous" class="finance-button-secondary" type="button" disabled>&larr; Trang trước</button><span id="pagination" aria-live="polite"></span><button id="next" class="finance-button-secondary" type="button" disabled>Trang sau &rarr;</button></div></section>
<section id="audit-detail" class="finance-panel" aria-live="polite" aria-label="Chi tiết nhật ký"></section>
<%@ include file="../report/_footer.jsp" %>
