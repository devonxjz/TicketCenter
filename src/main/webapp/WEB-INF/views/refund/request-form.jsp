<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List,vn.ticketscenter.fulfillment.refund.repository.RefundRequestStore.OrderOption" %>
<%! private static String h(Object v){return v==null?"":v.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");} %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Yêu cầu hoàn vé · TicketCenter</title>
<jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
<link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/buyer-ui.css">
</head>
<body class="buyer-page">
<jsp:include page="/WEB-INF/views/layout/header.jsp" /><main class="refund-container refund-panel" id="app" data-base="<%=h(request.getContextPath())%>" data-csrf="<%=h(request.getAttribute("csrf"))%>" data-order="<%=h(request.getAttribute("selectedOrder"))%>">
<a href="<%=h(request.getContextPath())%>/my-refunds">← Theo dõi yêu cầu của tôi</a><h1>Yêu cầu hoàn vé</h1>
<p class="policy">Chỉ gửi trước giờ bắt đầu sự kiện, với vé chưa sử dụng. Số tiền hoàn là số tiền thực trả của từng vé. Vé được chọn sẽ ngừng dùng để vào cửa ngay khi gửi yêu cầu và chờ quản trị duyệt.</p>
<label for="order">Chọn đơn hàng đã thanh toán</label><select id="order"><option value="">Chọn đơn hàng</option>
<% for(var order:(List<OrderOption>)request.getAttribute("refundOrders")){ %><option value="<%=h(order.orderId())%>"><%=h(order.eventName())%></option><% } %></select>
<div id="tickets" aria-live="polite"></div><p id="total" class="total">Số tiền dự kiến: 0 ₫</p>
<form id="form"><label for="reason">Lý do hoàn vé (10–500 ký tự)</label><textarea id="reason" rows="4" minlength="10" maxlength="500" required></textarea><p class="muted">Yêu cầu sẽ được kiểm tra lại theo trạng thái vé và thời gian trên hệ thống.</p><button id="submit" disabled>Kiểm tra và gửi yêu cầu</button></form><div id="message" role="status"></div>
<dialog id="confirm"><h2>Xác nhận yêu cầu</h2><p id="confirmation"></p><p>Những vé này sẽ chuyển sang chờ hoàn tiền và không thể check-in.</p><button id="send">Đồng ý gửi</button> <button id="cancel" type="button">Quay lại</button></dialog>
</main><script src="${pageContext.request.contextPath}/assets/refund/request-form.js"></script><jsp:include page="/WEB-INF/views/layout/footer.jsp" />
</body></html>
