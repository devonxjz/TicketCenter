<%@ page contentType="text/html;charset=UTF-8" %>
<%@ page import="vn.ticketscenter.settlement.controller.FinanceHtml" %>
<%
    request.setAttribute("financeTitle", "Quyết toán & chi trả");
    request.setAttribute("financePage", "settlements");
    request.setAttribute("financeAdmin", true);
    request.setAttribute("financeWritable", true);
    request.setAttribute(
            "financeIntro",
            "Đối soát doanh thu của từng sự kiện, giải quyết giao dịch còn chờ và chốt bảng kê trước khi chi trả cho tổ chức.");
%>
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
            <tbody id="ready-events"><%= FinanceHtml.readySettlementRows(request.getAttribute("readyEvents")) %></tbody>
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
            <%= FinanceHtml.options(request.getAttribute("events"),"title") %>
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
    <%
        if ("fixture-simulated".equals(request.getAttribute("payoutMode"))) {
    %>
    <p class="finance-callout">Chế độ kiểm thử: lệnh chi trả được mô phỏng, không chuyển tiền ngân hàng thật.</p>
    <%
        } else {
    %>
    <p class="finance-callout">Chưa kết nối dịch vụ chi trả. Bạn vẫn có thể chốt bảng kê và xem lịch sử.</p>
    <%
        }
    %>
    <form id="payout-form" class="finance-filter-grid"
        <%= "fixture-simulated".equals(request.getAttribute("payoutMode")) ? "" : "hidden" %>>
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
