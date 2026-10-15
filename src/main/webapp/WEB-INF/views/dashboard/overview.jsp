<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Tổng quan hệ thống"); request.setAttribute("financePage", "overview"); request.setAttribute("financeIntro", "Theo dõi số liệu toàn sàn và các hàng đợi cần xử lý. Doanh thu theo ngày đơn thanh toán được tách khỏi dòng tiền thực tế."); %>
<%@ include file="../report/_header.jsp" %>
<div class="finance-toolbar"><form id="filters"><button type="submit">Cập nhật tổng quan</button></form><a class="finance-link" href="<%= financeContext %>/finance/settlements">Xem bảng kê cần xử lý &rarr;</a></div>
<dl id="queue-summary" aria-label="Hàng đợi cần xử lý"></dl>
<p id="basis"></p><div id="summary" class="overview-summary"></div>

<section class="finance-panel"><div class="finance-panel-heading"><h2>Doanh thu và dòng tiền</h2><span class="finance-tag">7 ngày UTC</span></div>
<p class="finance-chart-legend"><span><i class="finance-legend-revenue" aria-hidden="true"></i>Doanh thu theo ngày đơn thanh toán</span><span><i class="finance-legend-cash" aria-hidden="true"></i>Dòng tiền ròng thực tế</span></p>
<div id="daily-chart" aria-live="polite"></div>

<div class="scroll" tabindex="0" aria-label="Số liệu biểu đồ"><table><caption>Số tiền nguyên VND theo ngày UTC</caption><thead><tr><th scope="col">Ngày UTC</th><th scope="col">Doanh thu gộp theo đơn thanh toán</th><th scope="col">Dòng tiền ròng thực tế</th></tr></thead><tbody id="daily-values"></tbody></table></div></section>
<%@ include file="../report/_footer.jsp" %>
