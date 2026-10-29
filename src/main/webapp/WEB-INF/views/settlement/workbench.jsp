<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<c:set var="financeTitle" value="Quyết toán & chi trả" scope="request" />
<c:set var="financePage" value="settlements" scope="request" />
<c:set var="financeAdmin" value="${true}" scope="request" />
<c:set var="financeWritable" value="${true}" scope="request" />
<c:set var="financeIntro" value="Đối soát doanh thu của từng sự kiện, giải quyết giao dịch còn chờ và chốt bảng kê trước khi chi trả cho tổ chức." scope="request" />
<%@ include file="../finance/_header.jsp" %>
<section class="finance-panel">
    <div class="finance-panel-heading">
        <h2>Sự kiện có thể quyết toán</h2>
        <span class="finance-tag">Đã kết thúc · Chưa chốt</span>
    </div>
    <p class="finance-muted">Các sự kiện đã kết thúc, có đơn hàng đã thanh toán và không còn khoản thanh toán hoặc hoàn
        tiền cần xử lý. Chọn “Xem bảng kê” để kiểm tra, tính lại và chốt quyết toán.</p>
    <div class="scroll" tabindex="0" aria-label="Sự kiện có thể quyết toán">
        <table>
            <caption class="finance-table-caption">Thời gian kết thúc theo giờ Việt Nam</caption>
            <thead>
                <tr>
                    <th>Sự kiện</th>
                    <th>Kết thúc</th>
                    <th>Thao tác</th>
                </tr>
            </thead>
            <tbody id="ready-events">
                <c:if test="${empty readyEvents}">
                    <tr><td colspan="3">Chưa có sự kiện đã kết thúc đủ điều kiện quyết toán.</td></tr>
                </c:if>
                <c:forEach items="${readyEvents}" var="event">
                    <tr>
                        <td><c:out value="${event.title}" /></td>
                        <td>${event.endTime}</td>
                        <td><button type="button" class="finance-button-secondary" data-ready-event="${event.id}"
                                data-ready-title="<c:out value='${event.title}' />">Xem bảng kê</button></td>
                    </tr>
                </c:forEach>
            </tbody>
        </table>
    </div>
</section>
<section class="finance-panel">
    <div class="finance-panel-heading">
        <h2>Chọn sự kiện đã kết thúc</h2>
        <span class="finance-tag">Bảng kê theo sự kiện</span>
    </div>
    <label>Sự kiện
        <select id="event" required>
            <option value="">Chọn sự kiện đã kết thúc</option>
            <c:forEach items="${events}" var="option"><option value="${fn:escapeXml(option.id)}"><c:out value="${option.title}" /></option></c:forEach>
        </select>
    </label>
</section>
<dl id="summary"></dl>
<section class="finance-panel">
    <div class="finance-panel-heading">
        <h2>Điều kiện chốt bảng kê</h2>
    </div>
    <p class="finance-muted">Chỉ tính lại bảng kê nháp. Các khoản thanh toán và hoàn tiền còn chờ phải được xử lý trước
        khi chốt.</p>
    <ul id="blockers"></ul>
    <div class="finance-form-actions">
        <button id="recalculate" class="finance-button-secondary" disabled>Tính lại bảng kê</button>
        <button id="confirm" class="finance-primary-action" disabled>Chốt quyết toán</button>
    </div>
</section>
<section class="finance-panel">
    <div class="finance-panel-heading">
        <h2>Chi trả cho tổ chức</h2>
        <span class="finance-tag">Sau khi chốt bảng kê</span>
    </div>
    <c:set var="payoutSimulated" value="${payoutMode eq 'fixture-simulated'}" />
    <c:choose>
        <c:when test="${payoutSimulated}">
            <p class="finance-callout">Chế độ kiểm thử: lệnh chi trả được mô phỏng, không chuyển tiền ngân hàng thật.</p>
        </c:when>
        <c:otherwise>
            <p class="finance-callout">Chưa kết nối dịch vụ chi trả. Bạn vẫn có thể chốt bảng kê và xem lịch sử.</p>
        </c:otherwise>
    </c:choose>
    <form id="payout-form" class="finance-filter-grid"
        ${payoutSimulated ? '' : 'hidden'}>
        <label>Số tiền (VND)
            <input name="amount" inputmode="numeric" pattern="[0-9]+" required>
        </label>
        <label>Số tài khoản
            <input name="bankAccountNumber" maxlength="64" required>
        </label>
        <label>Ngân hàng
            <input name="bankName" maxlength="100" required>
        </label>
        <label>Tên chủ tài khoản
            <input name="accountHolderName" maxlength="200" required>
        </label>
        <label>Ghi chú
            <input name="note" maxlength="500">
        </label>
        <div class="finance-form-actions">
            <button id="payout-submit" type="submit" disabled>Tạo lệnh chi trả</button>
            <button id="reconcile" class="finance-button-secondary" type="button" hidden>Xác minh lại kết quả đang
                chờ</button>
        </div>
    </form>
</section>
<section class="finance-panel">
    <div class="finance-panel-heading">
        <h2>Lịch sử chi trả</h2>
        <span class="finance-tag">Trạng thái từng lệnh</span>
    </div>
    <div class="scroll" tabindex="0" aria-label="Lịch sử chi trả">
        <table>
            <caption class="finance-table-caption">Lịch sử chi trả cho bảng kê đã chọn</caption>
            <thead>
                <tr>
                    <th>Thời gian UTC</th>
                    <th>Số tiền (VND)</th>
                    <th>Trạng thái</th>
                    <th>Mã chuyển tiền</th>
                </tr>
            </thead>
            <tbody id="payouts"></tbody>
        </table>
    </div>
</section>
<%@ include file="../finance/_footer.jsp" %>
