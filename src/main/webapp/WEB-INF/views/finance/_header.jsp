<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="financeContext" value="${fn:escapeXml(pageContext.request.contextPath)}" />
<c:set var="financePageAdmin" value="${financeAdmin eq true}" />
<c:set var="financeIsWritable" value="${financeWritable eq true}" />
<!doctype html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <meta name="context-path" content="${financeContext}">
    <meta name="csrf-token" content="${fn:escapeXml(sessionScope.TC_CSRF_TOKEN)}">
    <title>${fn:escapeXml(financeTitle)} · TicketsCenter</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <link rel="stylesheet" href="${financeContext}/assets/css/style.css">
    <link rel="stylesheet" href="${financeContext}/assets/css/finance.css">
    <link rel="stylesheet" href="${financeContext}/assets/css/production-ui.css">
    <link rel="stylesheet" href="${financeContext}/assets/css/admin-ui.css">
    <link rel="stylesheet" href="${financeContext}/assets/css/theme-components.css?v=20261014-2">
    <script defer src="${financeContext}/assets/js/core/tc-core.js"></script>
    <script defer src="${financeContext}/assets/js/core/pagination-chart.js"></script>
    <script defer src="${financeContext}/assets/js/layout/site-header.js?v=4"></script>
</head>
<body class="finance-body">
    <a class="finance-skip" href="#finance-content">Bỏ qua menu, đến nội dung</a>
    <c:choose>
        <c:when test="${financePageAdmin}">
            <jsp:include page="/WEB-INF/views/layout/admin-navbar.jsp" />
        </c:when>
        <c:otherwise>
            <jsp:include page="/WEB-INF/views/layout/header.jsp" />
        </c:otherwise>
    </c:choose>
    <div class="dashboard-layout finance-layout">
        <%-- the sidebars mark the finance page under their own tab names --%>
        <c:choose>
            <c:when test="${financePage eq 'overview'}">
                <c:set var="activeTab" value="dashboard" scope="request" />
            </c:when>
            <c:when test="${financePage eq 'audit'}">
                <c:set var="activeTab" value="audit-logs" scope="request" />
            </c:when>
            <c:otherwise>
                <c:set var="activeTab" value="${financePage}" scope="request" />
            </c:otherwise>
        </c:choose>
        <c:choose>
            <c:when test="${financePageAdmin}">
                <jsp:include page="/WEB-INF/views/layout/admin-sidebar.jsp" />
            </c:when>
            <c:otherwise>
                <jsp:include page="/WEB-INF/views/layout/organizer-sidebar.jsp" />
            </c:otherwise>
        </c:choose>
        <main class="dashboard-content finance-content" id="${financeIsWritable ? 'finance-app' : 'finance-readside'}"
            data-context="${financeContext}" data-page="${fn:escapeXml(financePage)}"
            data-admin="${financePageAdmin}"
            data-organization="${fn:escapeXml(financeOrganizationId)}"
            data-csrf="${fn:escapeXml(csrf)}"
            data-payout-mode="${fn:escapeXml(payoutMode)}">
            <div id="finance-content" tabindex="-1"></div>
            <header class="finance-page-heading">
                <p class="finance-eyebrow">TicketsCenter / ${financePageAdmin ? 'Quản trị' : 'Ban tổ chức'} / Tài
                    chính</p>
                <h1>${fn:escapeXml(financeTitle)}</h1>
                <p>${fn:escapeXml(financeIntro)}</p>
            </header>
            <p id="notice" class="finance-notice" role="status"
                aria-live="polite">${financeIsWritable ? '' : 'Đang tải dữ liệu…'}</p>
            <noscript>
                <p class="finance-notice">Vui lòng bật JavaScript để tải số liệu và sử dụng các thao tác tài chính.</p>
            </noscript>
