<%@ page pageEncoding="UTF-8" %>
<label>Tổ chức<select name="organizationId" id="organization" aria-label="Tổ chức"></select></label>
<% if (!"overview".equals(request.getAttribute("financePage"))) { %><label>Sự kiện<select name="eventId" id="event" disabled><option value="">Tất cả sự kiện</option></select></label>
<label>Từ ngày (UTC)<input name="from" type="datetime-local" step="1" required></label>
<label>Đến ngày (UTC)<input name="to" type="datetime-local" step="1" required></label>
<input type="hidden" name="pageSize" value="6"><% } %>
