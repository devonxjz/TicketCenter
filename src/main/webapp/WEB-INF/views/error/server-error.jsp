<%@ page contentType="text/html;charset=UTF-8" language="java" isErrorPage="true" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <title>${fn:escapeXml(errorTitle)} — TicketsCenter</title>
    <jsp:include page="/WEB-INF/views/layout/meta-head.jsp" />
</head>
<body class="tc-error-page">
    <nav class="navbar navbar-custom">
        <div class="container d-flex align-items-center justify-content-between">
            <a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">TicketsCenter</a>
            <a href="${pageContext.request.contextPath}/" class="small fw-semibold text-muted text-decoration-none"><i
                    class="fa-solid fa-house me-1"></i> Trang chủ</a>
        </div>
    </nav>
    <main class="container tc-error-main">
        <section class="tc-error-card" aria-labelledby="error-heading">
            <i class="fa-solid fa-triangle-exclamation tc-error-icon" aria-hidden="true"></i>
            <p class="tc-error-code">${fn:escapeXml(errorStatus)}</p>
            <h1 id="error-heading" class="h3 fw-bold">${fn:escapeXml(errorTitle)}</h1>
            <p class="text-muted">${fn:escapeXml(errorMessage)}</p>
            <div class="tc-page-actions justify-content-center">
                <a class="btn-primary-custom" href="${pageContext.request.contextPath}/">Về trang chủ</a>
                <button type="button" class="btn-outline-custom" onclick="location.reload()">Thử tải lại</button>
            </div>
        </section>
    </main>
</body>
</html>
