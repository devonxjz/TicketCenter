<%@ page contentType="text/html;charset=UTF-8" %>
<% request.setAttribute("financeTitle", "Tổng quan tài chính"); request.setAttribute("financePage", "overview"); %>
<%@ include file="../report/_header.jsp" %>
<p>Số lượng hiện tại và dòng tiền trong ngày UTC. Hoa hồng theo ngày đơn được thanh toán, có thể khác dòng tiền thu thực tế.</p>
<form id="filters"><%@ include file="../report/_scope.jsp" %><button type="submit">Cập nhật tổng quan</button></form>
<p id="basis"></p><dl id="summary"></dl>
<%@ include file="../report/_footer.jsp" %>
