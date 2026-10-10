<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Báo cáo tài chính"); request.setAttribute("financePage", "reports"); %>
<%@ include file="_header.jsp" %>
<p>Chọn cách tính trước khi đối chiếu doanh thu và dòng tiền. Các mốc thời gian được nhập theo UTC; tiền hiển thị nguyên VND.</p>
<form id="filters"><%@ include file="_scope.jsp" %>
<label>Cách tính<select name="metric"><option value="COHORT_REVENUE">Doanh thu theo ngày đơn thanh toán</option><option value="CASH_FLOW">Dòng tiền thu và hoàn thực tế</option><option value="EVENT_SALES">Doanh số hiện tại / đã chốt theo sự kiện</option><option value="SETTLEMENT_BALANCE">Số dư quyết toán theo sự kiện</option></select></label>
<button type="submit">Xem báo cáo</button><button id="export" type="button">Tải CSV theo bộ lọc</button></form>
<p id="basis"></p><div class="scroll" tabindex="0" aria-label="Bảng báo cáo"><table><caption>Chi tiết báo cáo</caption><thead id="columns"></thead><tbody id="rows"></tbody></table></div>
<div aria-label="Phân trang"><button id="previous" type="button" disabled>Trang trước</button><span id="pagination" aria-live="polite"></span><button id="next" type="button" disabled>Trang sau</button></div>
<%@ include file="_footer.jsp" %>
