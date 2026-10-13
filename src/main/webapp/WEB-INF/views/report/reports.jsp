<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Báo cáo tài chính & doanh thu"); request.setAttribute("financePage", "reports"); request.setAttribute("financeIntro", "Đối chiếu doanh thu, hoàn tiền và số phải trả theo cùng bộ lọc. Tiền nguyên VND, thời gian theo UTC."); %>
<%@ include file="_header.jsp" %>
<section class="finance-panel" aria-labelledby="filter-title"><div class="finance-panel-heading"><h2 id="filter-title">Phạm vi báo cáo</h2><span class="finance-tag">UTC · VND</span></div>
<form id="filters" class="finance-filter-grid"><%@ include file="_scope.jsp" %>
<label>Khoảng thời gian<select id="report-period" name="period"><option value="all">Toàn bộ thời gian</option><option value="7">7 ngày gần nhất</option><option value="30">30 ngày gần nhất</option><option value="90">90 ngày gần nhất</option><option value="custom">Khoảng tùy chọn</option></select></label>
<label>Cách tính<select name="metric"><option value="COHORT_REVENUE">Doanh thu theo ngày đơn thanh toán</option><option value="CASH_FLOW">Dòng tiền thu và hoàn thực tế</option><option value="EVENT_SALES">Doanh số hiện tại / đã chốt theo sự kiện</option><option value="SETTLEMENT_BALANCE">Số dư quyết toán theo sự kiện</option></select></label>
<div class="finance-form-actions"><button type="submit">Xem báo cáo</button><button id="export" class="finance-button-secondary" type="button">Tải CSV theo bộ lọc &darr;</button></div></form></section>
<p id="report-period-summary" role="status" aria-live="polite"></p><p id="basis"></p><section id="summary" aria-label="Tổng hợp toàn bộ dữ liệu theo bộ lọc"></section><section id="top-events" aria-label="Sự kiện có doanh thu cao nhất"></section>
<section class="finance-panel"><div class="finance-panel-heading"><h2>Chi tiết báo cáo</h2><span class="finance-tag">Theo bộ lọc</span></div>
<div class="scroll" tabindex="0" aria-label="Bảng báo cáo"><table><caption class="finance-table-caption">Chi tiết báo cáo</caption><thead id="columns"></thead><tbody id="rows"></tbody></table></div>
<div class="finance-pagination" aria-label="Phân trang"><button id="previous" class="finance-button-secondary" type="button" disabled>&larr; Trang trước</button><span id="pagination" aria-live="polite"></span><button id="next" class="finance-button-secondary" type="button" disabled>Trang sau &rarr;</button></div></section>
<%@ include file="_footer.jsp" %>
