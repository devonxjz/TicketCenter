<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.ticketscenter.report.controller.ReadsideHttp" %>
<%
    String financeContext = ReadsideHttp.escapeHtml(request.getContextPath());
    boolean financePageAdmin = Boolean.TRUE.equals(request.getAttribute("financeAdmin"));
    String financeCurrentPage = (String) request.getAttribute("financePage");
    boolean financeWritable = Boolean.TRUE.equals(request.getAttribute("financeWritable"));
%>
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="context-path" content="<%= financeContext %>">
    <meta name="csrf-token" content="<%= ReadsideHttp.escapeHtml((String) session.getAttribute("TC_CSRF_TOKEN")) %>">
    <title><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %> · TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="<%= financeContext %>/assets/css/style.css">
    <link rel="stylesheet" href="<%= financeContext %>/assets/css/finance.css">
    <link rel="stylesheet" href="<%= financeContext %>/assets/css/production-ui.css">
    <link rel="stylesheet" href="<%= financeContext %>/assets/css/admin-ui.css">
    <link rel="stylesheet" href="<%= financeContext %>/assets/css/theme-components.css?v=20261014-2">
    <script defer src="<%= financeContext %>/assets/js/core/tc-core.js"></script>
    <script defer src="<%= financeContext %>/assets/js/core/pagination-chart.js"></script>
    <script defer src="<%= financeContext %>/assets/js/layout/site-header.js?v=4"></script>
</head>
<body class="finance-body">
    <a class="finance-skip" href="#finance-content">Bỏ qua menu, đến nội dung</a>
    <%
        if (financePageAdmin) {
    %>
    <jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />
    <%
        } else {
    %>
    <jsp:include page="/WEB-INF/views/layout/header.jsp" />
    <%
        }
    %>
    <div class="dashboard-layout finance-layout">
        <%
            request.setAttribute(
                    "activeTab",
                    "overview".equals(financeCurrentPage)
                            ? "dashboard"
                            : "audit".equals(financeCurrentPage) ? "audit-logs" : financeCurrentPage);
        %>
        <%
            if (financePageAdmin) {
        %>
        <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp" />
        <%
            } else {
        %>
        <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
        <%
            }
        %>

        <main class="dashboard-content finance-content" id="<%= financeWritable ? "finance-app" : "finance-readside" %>"
            data-context="<%= financeContext %>" data-page="<%= ReadsideHttp.escapeHtml(financeCurrentPage) %>"
            data-admin="<%= financePageAdmin %>"
            data-organization="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeOrganizationId")) %>"
            data-csrf="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("csrf")) %>"
            data-payout-mode="<%= ReadsideHttp.escapeHtml((String) request.getAttribute("payoutMode")) %>">
            <div id="finance-content" tabindex="-1"></div>
            <header class="finance-page-heading">
                <p class="finance-eyebrow">TicketsCenter / <%= financePageAdmin ? "Quản trị" : "Ban tổ chức" %> / Tài
                    chính</p>
                <h1><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeTitle")) %></h1>
                <p><%= ReadsideHttp.escapeHtml((String) request.getAttribute("financeIntro")) %></p>
            </header>
            <p id="notice" class="finance-notice" role="status"
                aria-live="polite"><%= financeWritable ? "" : "Đang tải dữ liệu…" %></p>
            <noscript>
                <p class="finance-notice">Vui lòng bật JavaScript để tải số liệu và sử dụng các thao tác tài chính.</p>
            </noscript>
