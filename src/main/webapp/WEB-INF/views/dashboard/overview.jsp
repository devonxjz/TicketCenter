<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Tổng quan tài chính"); request.setAttribute("financePage", "overview"); %>
<%@ include file="../report/_header.jsp" %>
<p>Số lượng hiện tại và dòng tiền trong ngày UTC. Hoa hồng theo ngày đơn được thanh toán, có thể khác dòng tiền thu thực tế.</p>
<form id="filters"><%@ include file="../report/_scope.jsp" %><button type="submit">Cập nhật tổng quan</button></form>
<p id="basis"></p><dl id="summary"></dl>
<p>Số liệu có thể chậm tối đa một phút; thời điểm cập nhật được ghi ở trên.</p>
<h2>Doanh thu và dòng tiền · 7 ngày UTC</h2>
<p>Cột xanh: doanh thu gộp theo ngày đơn thanh toán. Cột nâu: dòng tiền ròng theo ngày thu tiền và chi hoàn, bao gồm thanh toán thừa. Dòng tiền âm nằm dưới mốc 0. Chiều cao cột dùng cùng một tỷ lệ; bảng dưới ghi số tiền chính xác.</p>
<div id="daily-chart" aria-live="polite"></div>
<div class="scroll" tabindex="0" aria-label="Số liệu biểu đồ"><table><caption>Số tiền nguyên VND theo ngày UTC</caption><thead><tr><th scope="col">Ngày UTC</th><th scope="col">Doanh thu gộp theo đơn thanh toán</th><th scope="col">Dòng tiền ròng thực tế</th></tr></thead><tbody id="daily-values"></tbody></table></div>
<%@ include file="../report/_footer.jsp" %>
