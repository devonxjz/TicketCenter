<%@ page pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<label>Tổ chức
    <select name="organizationId" id="organization" aria-label="Tổ chức"></select>
</label>
<c:if test="${financePage ne 'overview'}">
<label>Sự kiện
    <select name="eventId" id="event" disabled>
        <option value="">Tất cả sự kiện</option>
    </select>
</label>
<label>Từ thời điểm (UTC, bao gồm)
    <input name="from" type="datetime-local" step="1" required>
</label>
<label>Đến thời điểm (UTC, không bao gồm)
    <input name="to" type="datetime-local" step="1" required>
</label>
<label>Số dòng mỗi trang
    <select name="pageSize">
        <option value="20">20</option>
        <option value="50">50</option>
        <option value="100">100</option>
    </select>
</label>
</c:if>
