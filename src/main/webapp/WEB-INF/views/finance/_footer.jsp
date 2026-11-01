<%@ page pageEncoding="UTF-8" %>
<footer class="finance-page-footer">
    <span>TicketsCenter · Tài chính</span>
    <span>Số liệu theo phạm vi và bộ lọc đã chọn</span>
</footer>
</main>
</div>
<c:choose>
    <c:when test="${financeIsWritable}">
        <c:set var="financeScript" value="finance/settlement-workbench.js" />
    </c:when>
    <c:when test="${financePage eq 'reports'}">
        <c:set var="financeScript" value="finance/reports.js" />
    </c:when>
    <c:otherwise>
        <c:set var="financeScript" value="finance/overview-audit.js" />
    </c:otherwise>
</c:choose>
<script src="${financeContext}/assets/js/${financeScript}?v=6" defer></script>
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js" defer></script>
<c:if test="${financePageAdmin}">
    <script src="${financeContext}/assets/js/admin/notification-bell.js" defer></script>
</c:if>
</body>
</html>
