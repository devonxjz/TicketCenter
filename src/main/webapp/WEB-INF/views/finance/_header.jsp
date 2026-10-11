<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.ticketscenter.report.controller.ReadsideHttp" %>
<% String financeContext = ReadsideHttp.escapeHtml(request.getContextPath());
   boolean financePageAdmin = Boolean.TRUE.equals(request.getAttribute("financeAdmin"));
   String financeCurrentPage = (String) request.getAttribute("financePage");
   boolean financeWritable = Boolean.TRUE.equals(request.getAttribute("financeWritable")); %>
<!doctype html>
<html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="context-path" content="<%= financeContext %>">
<meta name="csrf-token" content="<%= ReadsideHttp.escapeHtml((String) session.getAttribute("TC_CSRF_TOKEN")) %>">
<title><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %> · TicketsCenter</title>
<link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
<link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
<link rel="stylesheet" href="<%= financeContext %>/assets/css/style.css">
<link rel="stylesheet" href="<%= financeContext %>/assets/css/finance.css">
</head><body class="finance-body">
<a class="finance-skip" href="#finance-content">Bỏ qua menu, đến nội dung</a>
<% if (financePageAdmin) { %>
<jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />
<% } else { %>
<header class="finance-partner-bar">
  <div class="finance-brand"><a class="navbar-brand-custom" href="<%= financeContext %>/">TicketsCenter</a><span class="status-pill status-neutral">Cổng Đối Tác</span></div>
  <a class="finance-link" href="<%= financeContext %>/organizer/dashboard">Về tổng quan tổ chức &rarr;</a>
</header>
<% } %>
<div class="dashboard-layout finance-layout">
<aside class="dashboard-sidebar finance-sidebar">
  <p class="sidebar-title"><%= financePageAdmin ? "Quản trị tài chính" : "Tài chính tổ chức" %></p>
  <nav aria-label="Điều hướng tài chính"><ul class="sidebar-menu">
  <% if (financePageAdmin) { %>
    <li><a href="<%= financeContext %>/finance/overview" <%= "overview".equals(financeCurrentPage) ? "aria-current=\"page\" class=\"active\"" : "" %>>Tổng quan</a></li>
  <% } %>
    <li><a href="<%= financeContext %>/finance/reports" <%= "reports".equals(financeCurrentPage) ? "aria-current=\"page\" class=\"active\"" : "" %>>Báo cáo doanh thu</a></li>
  <% if (financePageAdmin) { %>
    <li><a href="<%= financeContext %>/finance/commission" <%= "commission".equals(financeCurrentPage) ? "aria-current=\"page\" class=\"active\"" : "" %>>Chính sách hoa hồng</a></li>
    <li><a href="<%= financeContext %>/finance/settlements" <%= "settlements".equals(financeCurrentPage) ? "aria-current=\"page\" class=\"active\"" : "" %>>Quyết toán &amp; chi trả</a></li>
    <li><a href="<%= financeContext %>/finance/audit" <%= "audit".equals(financeCurrentPage) ? "aria-current=\"page\" class=\"active\"" : "" %>>Nhật ký hệ thống</a></li>
  <% } %>
  </ul></nav>
  <div class="finance-sidebar-secondary">
    <p class="sidebar-title">Điều hướng chung</p>
    <ul class="sidebar-menu">
    <% if (financePageAdmin) { %>
      <li><a href="<%= financeContext %>/admin/orgs/approval">Duyệt hồ sơ tổ chức &rarr;</a></li>
      <li><a href="<%= financeContext %>/admin/events/approval">Duyệt sự kiện &rarr;</a></li>
      <li><a href="<%= financeContext %>/admin/refunds/approval">Duyệt hoàn vé &rarr;</a></li>
    <% } %>
      <li><a href="<%= financeContext %>/organizer/dashboard">Cổng Ban tổ chức &rarr;</a></li>
      <li><a href="<%= financeContext %>/">Sàn bán vé &rarr;</a></li>
    </ul>
    <p class="finance-sidebar-note">Tiền nguyên VND<br>Thời gian theo UTC</p>
  </div>
</aside>
<main class="dashboard-content finance-content" id="<%= financeWritable ? "finance-app" : "finance-readside" %>"
 data-context="<%= financeContext %>" data-page="<%= ReadsideHttp.escapeHtml(financeCurrentPage) %>" data-admin="<%= financePageAdmin %>"
 data-organization="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeOrganizationId")) %>"
 data-csrf="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("csrf")) %>" data-payout-mode="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("payoutMode")) %>">
<div id="finance-content" tabindex="-1"></div>
<header class="finance-page-heading">
  <p class="finance-eyebrow">TicketsCenter / <%= financePageAdmin ? "Quản trị" : "Ban tổ chức" %> / Tài chính</p>
  <h1><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %></h1>
  <p><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeIntro")) %></p>
</header>
<p id="notice" class="finance-notice" role="status" aria-live="polite"><%= financeWritable ? "" : "Đang tải dữ liệu…" %></p>
<noscript><p class="finance-notice">Vui lòng bật JavaScript để tải số liệu và sử dụng các thao tác tài chính.</p></noscript>
