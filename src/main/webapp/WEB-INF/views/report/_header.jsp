<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.ticketscenter.report.controller.ReadsideHttp" %>
<% String financeContext = ReadsideHttp.escapeHtml(request.getContextPath());
   boolean financePageAdmin = Boolean.TRUE.equals(request.getAttribute("financeAdmin")); %>
<!doctype html><html lang="vi"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %> · TicketCenter</title>
<link rel="stylesheet" href="<%= financeContext %>/assets/css/finance.css">
</head><body><main id="finance-readside" data-context="<%= financeContext %>" data-page="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("financePage")) %>" data-admin="<%= financePageAdmin %>" data-organization="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeOrganizationId")) %>">
<nav aria-label="Tài chính"><a href="<%= financeContext %>/finance/reports">Báo cáo</a>
<% if (financePageAdmin) { %><a href="<%= financeContext %>/finance/commission">Hoa hồng</a><a href="<%= financeContext %>/finance/settlements">Quyết toán</a><% } %></nav>
<h1><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %></h1><p id="notice" role="status" aria-live="polite">Đang tải dữ liệu…</p>
<noscript><p>Vui lòng bật JavaScript để chọn bộ lọc và xem dữ liệu tài chính.</p></noscript>
