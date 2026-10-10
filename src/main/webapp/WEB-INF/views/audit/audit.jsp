<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Nhật ký tài chính"); request.setAttribute("financePage", "audit"); %>
<%@ include file="../report/_header.jsp" %>
<p>Nhật ký chỉ dành cho quản trị viên. Chi tiết hiển thị thông tin tài chính được phép công khai trong hệ thống.</p>
<form id="filters"><%@ include file="../report/_scope.jsp" %>
<label>Thao tác<input name="action" maxlength="100" pattern="[A-Z][A-Z0-9_]{0,99}" placeholder="Ví dụ: SETTLEMENT_CONFIRMED"></label>
<label>Loại đối tượng<input name="entityType" maxlength="100" pattern="[A-Za-z][A-Za-z0-9_]{0,99}" placeholder="Ví dụ: Settlement"></label>
<label>Mã đối tượng<input name="entityId" maxlength="100" pattern="[A-Za-z0-9_-]{1,100}"></label><button type="submit">Lọc nhật ký</button></form>
<p id="basis"></p><div class="scroll" tabindex="0" aria-label="Bảng nhật ký"><table><caption>Nhật ký mới nhất trước</caption><thead id="columns"></thead><tbody id="rows"></tbody></table></div>
<div aria-label="Phân trang"><button id="previous" type="button" disabled>Trang trước</button><span id="pagination" aria-live="polite"></span><button id="next" type="button" disabled>Trang sau</button></div>
<section id="audit-detail" aria-live="polite" aria-label="Chi tiết nhật ký"></section>
<%@ include file="../report/_footer.jsp" %>
