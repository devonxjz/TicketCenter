<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!-- Top Admin Bar -->
<nav class="admin-navbar sticky-top">
    <div class="container-fluid d-flex align-items-center justify-content-between p-0">
        <div class="d-flex align-items-center gap-3">
            <a class="navbar-brand-custom" href="${pageContext.request.contextPath}/">
                TicketsCenter
            </a>
            <span class="status-pill status-neutral">Quản Trị Hệ Thống</span>
        </div>

        <div class="d-flex align-items-center gap-3">
            <!-- Admin Notification Bell -->
            <div class="dropdown" id="admin-notification-container">
                <button class="btn btn-sm btn-outline-dark position-relative rounded-0" type="button"
                    id="adminNotificationBell" data-bs-toggle="dropdown" aria-expanded="false"
                    title="Thông báo hệ thống">
                    <i class="fa-solid fa-bell"></i>
                    <span id="adminNotificationBadge"
                        class="position-absolute top-0 start-100 translate-middle badge rounded-pill bg-danger d-none"
                        style="font-size: 0.65rem;">
                        0
                    </span>
                </button>
                <ul class="dropdown-menu dropdown-menu-end shadow-sm rounded-0 border-secondary-subtle p-0"
                    id="adminNotificationDropdown" style="width: 360px; max-height: 420px; overflow-y: auto;"
                    aria-labelledby="adminNotificationBell">
                    <li class="p-3 border-bottom bg-light d-flex justify-content-between align-items-center">
                        <strong class="small text-dark">Yêu cầu tạo tổ chức chờ duyệt</strong>
                        <span id="adminNotificationCountText" class="badge bg-danger rounded-pill">0</span>
                    </li>
                    <div id="adminNotificationList">
                        <li class="p-3 text-center text-muted small" id="adminNotificationEmpty">Không có yêu cầu nào
                            cần duyệt</li>
                    </div>
                    <li class="p-2 text-center border-top bg-light">
                        <a href="${pageContext.request.contextPath}/admin/orgs/approval"
                            class="small text-decoration-none fw-semibold text-danger">Xem tất cả hồ sơ đối tác
                            &rarr;</a>
                    </li>
                </ul>
            </div>

            <c:if test="${not empty adminStatusBadge}">
                <span class="status-pill status-success">
                    <c:out value="${adminStatusBadge}" />
                </span>
            </c:if>
            <span class="small text-muted d-none d-sm-inline">
                <c:out value="${sessionScope.TC_CURRENT_USER.email()}" />
            </span>
            <a href="${pageContext.request.contextPath}/" class="btn btn-sm btn-outline-dark rounded-0 px-3">
                Xem sàn bán vé
            </a>
            <a href="${pageContext.request.contextPath}/auth/logout"
                class="btn btn-sm btn-outline-danger rounded-0 px-3"
                onclick="event.preventDefault(); document.getElementById('logoutForm').submit();">
                Đăng xuất
            </a>
            <form id="logoutForm" action="${pageContext.request.contextPath}/auth/logout" method="post"
                style="display:none;">
                <input type="hidden" name="_csrf" value="${sessionScope.TC_CSRF_TOKEN}">
            </form>
        </div>
    </div>
</nav>
