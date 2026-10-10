<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!-- Left Sidebar -->
<aside class="dashboard-sidebar">
    <c:choose>
        <c:when test="${not empty sessionScope.TC_CURRENT_USER && sessionScope.TC_CURRENT_USER.organizationRole == 'CHECK_IN_STAFF' && !sessionScope.TC_CURRENT_USER.isAdmin()}">
            <div class="sidebar-title">Soát vé tại cửa</div>
            <ul class="sidebar-menu">
                <li><a href="${pageContext.request.contextPath}/organizer/checkin-select" class="${activeTab == 'checkin' || activeTab == 'checkin-select' || empty activeTab ? 'active' : ''}">• Chọn sự kiện soát vé</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/checkin-scanner" class="${activeTab == 'checkin-scanner' ? 'active' : ''}">• Máy quét camera QR</a></li>
            </ul>
        </c:when>
        <c:otherwise>
            <div class="sidebar-title">Quản lý tổ chức</div>
            <ul class="sidebar-menu">
                <li><a href="${pageContext.request.contextPath}/organizer/dashboard" class="${activeTab == 'dashboard' || (empty activeTab && empty param.active) ? 'active' : ''}">• Tổng quan</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/events" class="${activeTab == 'events' ? 'active' : ''}">• Danh sách sự kiện</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/event-editor" class="${activeTab == 'event-editor' ? 'active' : ''}">• Tạo mới sự kiện</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/coupons" class="${activeTab == 'coupons' ? 'active' : ''}">• Mã khuyến mãi</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/members" class="${activeTab == 'members' ? 'active' : ''}">• Nhân sự & Phân quyền</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/checkin-select" class="${activeTab == 'checkin' || activeTab == 'checkin-select' ? 'active' : ''}">• Soát vé tại cửa</a></li>
                <li><a href="${pageContext.request.contextPath}/organizer/reports" class="${activeTab == 'reports' ? 'active' : ''}">• Báo cáo & Quyết toán</a></li>
            </ul>
        </c:otherwise>
    </c:choose>

    <div class="sidebar-title">Hệ thống</div>
    <ul class="sidebar-menu">
        <li><a href="${pageContext.request.contextPath}/">• Xem sàn bán vé</a></li>
    </ul>
</aside>
