<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!-- Admin Sidebar -->
<aside class="dashboard-sidebar">
    <div class="sidebar-title">Trung tâm Quản trị</div>
    <ul class="sidebar-menu">
        <li><a href="${pageContext.request.contextPath}/admin/dashboard" class="${activeTab == 'dashboard' || param.active == 'dashboard' || (empty activeTab && empty param.active) ? 'active' : ''}">• Tổng quan vận hành</a></li>
        <li><a href="${pageContext.request.contextPath}/admin/org-approval" class="${activeTab == 'org-approval' || param.active == 'org-approval' ? 'active' : ''}">• Duyệt hồ sơ tổ chức</a></li>
        <li><a href="${pageContext.request.contextPath}/admin/event-approval" class="${activeTab == 'event-approval' || param.active == 'event-approval' ? 'active' : ''}">• Duyệt & Hủy sự kiện</a></li>
        <li><a href="${pageContext.request.contextPath}/admin/refund-approval" class="${activeTab == 'refund-approval' || param.active == 'refund-approval' ? 'active' : ''}">• Xử lý hoàn vé</a></li>
        <li><a href="${pageContext.request.contextPath}/admin/settlements" class="${activeTab == 'settlements' || param.active == 'settlements' ? 'active' : ''}">• Hoa hồng & Quyết toán</a></li>
        <li><a href="${pageContext.request.contextPath}/admin/audit-logs" class="${activeTab == 'audit-logs' || param.active == 'audit-logs' ? 'active' : ''}">• Nhật ký hệ thống</a></li>
    </ul>

    <div class="sidebar-title">Điều hướng nhanh</div>
    <ul class="sidebar-menu">
        <li><a href="${pageContext.request.contextPath}/">• Cổng người mua</a></li>
        <li><a href="${pageContext.request.contextPath}/organizer/dashboard">• Cổng Ban tổ chức</a></li>
    </ul>
</aside>
