<%@ page pageEncoding="UTF-8" %>
<footer class="finance-page-footer">
    <span>TicketsCenter · Tài chính</span>
    <span>Số liệu theo phạm vi và bộ lọc đã chọn</span>
</footer>
</main>
</div>
<script
    src="<%= financeContext %>/assets/js/<%=
        financeWritable
                ? "finance/settlement-workbench.js"
                : "reports".equals(financeCurrentPage) ? "finance/reports.js" : "finance/overview-audit.js"
    %>?v=6"
    defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js" defer></script>
<%
    if (financePageAdmin) {
%>
<script src="<%= financeContext %>/assets/js/admin/notification-bell.js" defer></script>
<%
    }
%>
</body>
</html>
