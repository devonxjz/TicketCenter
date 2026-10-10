/* Finance read-side pages: safe text rendering and one filter contract for JSON/CSV. */
(function () {
  'use strict';
  const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
  function utc(value) {
    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(?::\d{2}(?:\.\d{1,3})?)?$/.test(value || ''))
      throw new Error('Nhập đầy đủ thời gian UTC.');
    const date = new Date(value + 'Z');
    if (!Number.isFinite(date.getTime()) || date.toISOString().slice(0, 16) !== value.slice(0, 16))
      throw new Error('Thời gian UTC không hợp lệ.');
    return date.toISOString();
  }
  function query(values, pageType, page) {
    const from = utc(values.from), to = utc(values.to);
    if (from >= to) throw new Error('Thời gian kết thúc phải sau thời gian bắt đầu.');
    const size = values.pageSize || '20';
    if (!/^[0-9]+$/.test(size) || Number(size) < 1 || Number(size) > 100 || !Number.isInteger(page) || page < 1)
      throw new Error('Phân trang không hợp lệ.');
    const result = new URLSearchParams({ from, to, page: String(page), pageSize: size });
    for (const name of ['organizationId', 'eventId']) {
      if (values[name]) {
        if (!uuid.test(values[name])) throw new Error('Tổ chức hoặc sự kiện không hợp lệ.');
        result.set(name, values[name]);
      }
    }
    if (pageType === 'reports') {
      const metric = values.metric || 'COHORT_REVENUE';
      if (!['COHORT_REVENUE', 'CASH_FLOW', 'EVENT_SALES', 'SETTLEMENT_BALANCE'].includes(metric))
        throw new Error('Loại báo cáo không hợp lệ.');
      result.set('metric', metric);
    } else if (pageType === 'audit') {
      for (const name of ['action', 'entityType', 'entityId']) if (values[name]) result.set(name, values[name]);
    }
    return result;
  }
  function formatVnd(value) {
    if (value == null || !/^-?[0-9]+$/.test(String(value))) return '—';
    return new Intl.NumberFormat('vi-VN').format(BigInt(value)) + ' ₫';
  }
  async function fetchResponse(url, type, network = fetch) {
    const response = await network(url, { credentials: 'same-origin', cache: 'no-store', headers: { Accept: type } });
    if (!response.ok || !(response.headers.get('Content-Type') || '').toLowerCase().startsWith(type)) {
      const messages = { 401: 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
        403: 'Bạn không có quyền xem dữ liệu này.', 404: 'Không tìm thấy dữ liệu trong phạm vi đã chọn.',
        400: 'Bộ lọc không hợp lệ. Kiểm tra lại thông tin.', 503: 'Dữ liệu tạm thời không khả dụng. Vui lòng thử lại.' };
      let reference = '';
      if ((response.headers.get('Content-Type') || '').includes('application/json')) {
        try { const payload = await response.json(); if (uuid.test(payload.error?.correlationId || '')) reference = ' Mã tra cứu: ' + payload.error.correlationId; } catch (_) { /* Fixed error only. */ }
      }
      throw new Error((messages[response.status] || 'Không thể tải dữ liệu. Vui lòng đăng nhập lại hoặc thử lại.') + reference);
    }
    return response;
  }
  const api = { query, formatVnd, fetchResponse };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof document === 'undefined') return;

  const app = document.getElementById('finance-readside');
  if (!app) return;
  const context = app.dataset.context || '', pageType = app.dataset.page, isAdmin = app.dataset.admin === 'true';
  const form = document.getElementById('filters'), org = document.getElementById('organization'), event = document.getElementById('event');
  const notice = document.getElementById('notice'), tbody = document.getElementById('rows'), head = document.getElementById('columns');
  const previous = document.getElementById('previous'), next = document.getElementById('next'), pagination = document.getElementById('pagination');
  let page = 1, sequence = 0, scopeSequence = 0;
  const money = new Set(['grossRevenue', 'totalRefund', 'remainingAmount', 'totalCommission', 'netPayable',
    'ticketCaptured', 'compensationCaptured', 'ticketRefund', 'compensationRefund', 'netCashFlow',
    'paidAmount', 'pendingAmount', 'availableToPay', 'amount', 'todayCohortCommission']);
  const labels = {
    eventId: 'Sự kiện', organizationId: 'Tổ chức', paidOrderCount: 'Đơn đã thanh toán', grossRevenue: 'Doanh thu gộp',
    totalRefund: 'Hoàn vé', remainingAmount: 'Sau hoàn vé', totalCommission: 'Hoa hồng', netPayable: 'Phải trả tổ chức',
    ticketCaptured: 'Thu tiền vé', compensationCaptured: 'Thu thanh toán thừa', ticketRefund: 'Chi hoàn vé',
    compensationRefund: 'Chi hoàn thanh toán thừa', netCashFlow: 'Dòng tiền ròng', eventTitle: 'Tên sự kiện',
    eventStatus: 'Trạng thái sự kiện', endTime: 'Kết thúc (UTC)', ticketCount: 'Số vé', validTicketCount: 'Vé hợp lệ',
    refundedTicketCount: 'Vé đã hoàn', settlementId: 'Mã quyết toán', settlementStatus: 'Trạng thái quyết toán',
    isConfirmed: 'Bảng kê', status: 'Trạng thái', paidAmount: 'Đã chi', pendingAmount: 'Đang chờ chi',
    availableToPay: 'Có thể chi', confirmedAt: 'Chốt lúc (UTC)', createdAt: 'Thời gian (UTC)', action: 'Thao tác',
    entityType: 'Loại đối tượng', entityId: 'Mã đối tượng', actorId: 'Người thực hiện', id: 'Mã nhật ký',
    pendingOrganizations: 'Tổ chức chờ duyệt', pendingEvents: 'Sự kiện chờ duyệt', pendingRefunds: 'Yêu cầu hoàn chờ xử lý',
    draftSettlements: 'Quyết toán nháp', eventCount: 'Tổng sự kiện', paidOrders: 'Đơn đã thanh toán',
    todayCohortCommission: 'Hoa hồng của đơn thanh toán hôm nay', from: 'Từ (UTC)', to: 'Đến (UTC)', asOf: 'Cập nhật (UTC)'
  };
  const columns = {
    COHORT_REVENUE: ['eventId', 'organizationId', 'paidOrderCount', 'grossRevenue', 'totalRefund', 'remainingAmount', 'totalCommission', 'netPayable'],
    CASH_FLOW: ['eventId', 'organizationId', 'ticketCaptured', 'compensationCaptured', 'ticketRefund', 'compensationRefund', 'netCashFlow'],
    EVENT_SALES: ['eventId', 'eventTitle', 'eventStatus', 'endTime', 'paidOrderCount', 'ticketCount', 'validTicketCount', 'refundedTicketCount', 'grossRevenue', 'totalRefund', 'totalCommission', 'netPayable', 'settlementStatus', 'isConfirmed'],
    SETTLEMENT_BALANCE: ['settlementId', 'eventId', 'status', 'grossRevenue', 'totalRefund', 'totalCommission', 'netPayable', 'paidAmount', 'pendingAmount', 'availableToPay', 'confirmedAt'],
    audit: ['createdAt', 'action', 'entityType', 'entityId', 'actorId']
  };
  const basis = {
    ORDER_PAID_AT_WITH_REFUNDS_BEFORE_TO: 'Theo thời điểm đơn được thanh toán; hoàn vé hoàn tất trước mốc Đến. Hoa hồng tính trên từng đơn.',
    PAYMENT_PAID_AT_AND_REFUND_PROCESSED_AT: 'Theo thời điểm thu tiền và chi hoàn trong khoảng UTC đã chọn. Thanh toán thừa được tách riêng.',
    EVENT_END_TIME_CURRENT_OR_CONFIRMED_TOTALS: 'Lọc theo thời điểm kết thúc sự kiện; tổng hiện tại hoặc bảng kê đã chốt. Đây không phải lịch sử giao dịch theo ngày.'
  };
  function node(tag, text) { const element = document.createElement(tag); if (text != null) element.textContent = String(text); return element; }
  function values() { return Object.fromEntries(new FormData(form).entries()); }
  function display(name, value) {
    if (value == null) return '—';
    if (money.has(name)) return formatVnd(value);
    if (name === 'isConfirmed') return value ? 'Đã chốt' : 'Ước tính';
    if (name.endsWith('At') || name === 'endTime' || ['from', 'to', 'asOf'].includes(name)) {
      const date = new Date(value); return Number.isFinite(date.getTime()) ? date.toISOString().replace('T', ' ').replace('.000Z', ' UTC') : '—';
    }
    return String(value);
  }
  function clearResults() {
    tbody?.replaceChildren(); head?.replaceChildren();
    document.getElementById('summary')?.replaceChildren();
    document.getElementById('audit-detail')?.replaceChildren();
    if (previous) previous.disabled = true;
    if (next) next.disabled = true;
    if (pagination) pagination.textContent = '';
    const description = document.getElementById('basis'); if (description) description.textContent = '';
  }
  async function data(url) { return (await (await fetchResponse(context + url, 'application/json')).json()).data; }
  async function scope(selected) {
    const current = ++scopeSequence;
    if (event) { event.replaceChildren(new Option('Tất cả sự kiện', '')); event.disabled = true; }
    const result = await data('/api/reports/options' + (selected ? '?organizationId=' + encodeURIComponent(selected) : ''));
    if (current !== scopeSequence) return;
    org.replaceChildren();
    if (isAdmin) org.append(new Option('Tất cả tổ chức', ''));
    for (const item of result.organizations) org.append(new Option(item.name, item.id));
    org.value = selected || (isAdmin ? '' : result.organizations[0]?.id || '');
    if (event) {
      for (const item of result.events) event.append(new Option(item.title, item.id));
      event.disabled = !org.value;
    }
  }
  async function detail(id) {
    const panel = document.getElementById('audit-detail'); panel.replaceChildren();
    try {
      const result = await data('/api/admin/audit-logs/' + encodeURIComponent(id));
      const dl = node('dl');
      for (const key of ['id', 'createdAt', 'actorId', 'action', 'entityType', 'entityId']) {
        dl.append(node('dt', labels[key]), node('dd', display(key, result[key])));
      }
      panel.append(node('h2', 'Chi tiết nhật ký'), dl, node('h3', 'Thông tin được phép hiển thị'));
      const metadata = node('dl');
      for (const [key, value] of Object.entries(result.metadata || {})) metadata.append(node('dt', labels[key] || key), node('dd', display(key, value)));
      panel.append(Object.keys(result.metadata || {}).length ? metadata : node('p', 'Không có thông tin bổ sung.'));
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    } catch (error) { notice.textContent = error.message; }
  }
  function table(result) {
    const keys = columns[pageType === 'audit' ? 'audit' : result.metric];
    if (!keys || !Array.isArray(result.rows)) throw new Error('Dữ liệu báo cáo không hợp lệ.');
    const header = node('tr');
    for (const key of keys) { const th = node('th', labels[key]); th.scope = 'col'; header.append(th); }
    if (pageType === 'audit') header.append(node('th', 'Chi tiết'));
    head.append(header);
    for (const row of result.rows) {
      const tr = node('tr');
      for (const key of keys) tr.append(node('td', display(key, row[key])));
      if (pageType === 'audit') { const td = node('td'), button = node('button', 'Xem'); button.type = 'button'; button.addEventListener('click', () => detail(row.id)); td.append(button); tr.append(td); }
      tbody.append(tr);
    }
    if (!result.rows.length) { const td = node('td', 'Không có dữ liệu trong khoảng đã chọn.'); td.colSpan = keys.length + (pageType === 'audit' ? 1 : 0); const tr = node('tr'); tr.append(td); tbody.append(tr); }
    previous.disabled = result.page <= 1;
    next.disabled = result.page * result.pageSize >= result.total;
    pagination.textContent = 'Trang ' + result.page + ' · ' + result.total + ' dòng';
    document.getElementById('basis').textContent = (basis[result.timeBasis] || 'Nhật ký theo thời điểm phát sinh.') + ' Khoảng [' + display('from', result.from) + ', ' + display('to', result.to) + ').';
  }
  function overview(result) {
    const dl = document.getElementById('summary');
    for (const key of ['pendingOrganizations', 'pendingEvents', 'pendingRefunds', 'draftSettlements', 'eventCount', 'paidOrders']) {
      if (result[key] != null) dl.append(node('dt', labels[key]), node('dd', display(key, result[key])));
    }
    dl.append(node('dt', 'Dòng tiền hôm nay (UTC)'), node('dd', ''));
    for (const [key, value] of Object.entries(result.todayCashFlow || {})) dl.append(node('dt', labels[key] || key), node('dd', display(key, value)));
    dl.append(node('dt', labels.todayCohortCommission), node('dd', formatVnd(result.todayCohortCommission)));
    document.getElementById('basis').textContent = 'Ngày UTC [' + display('from', result.from) + ', ' + display('to', result.to) + '). ' + basis[result.commissionTimeBasis] + ' Cập nhật: ' + display('asOf', result.asOf) + '.';
  }
  async function load(newPage = 1) {
    const current = ++sequence;
    clearResults(); notice.textContent = 'Đang tải dữ liệu…';
    app.setAttribute('aria-busy', 'true');
    try {
      const input = values(); let url;
      if (pageType === 'overview') url = input.organizationId ? '/api/organizations/' + encodeURIComponent(input.organizationId) + '/overview' : '/api/admin/overview';
      else {
        const params = query(input, pageType, newPage);
        url = pageType === 'audit' ? '/api/admin/audit-logs' : isAdmin ? '/api/admin/reports' : '/api/organizations/' + encodeURIComponent(input.organizationId) + '/reports';
        url += '?' + params;
      }
      const result = await data(url);
      if (current !== sequence) return;
      if (pageType === 'overview') overview(result); else table(result);
      page = newPage; notice.textContent = '';
    } catch (error) { if (current === sequence) { clearResults(); notice.textContent = error.message; } }
    finally { if (current === sequence) app.setAttribute('aria-busy', 'false'); }
  }
  form.addEventListener('submit', e => { e.preventDefault(); load(1); });
  org.addEventListener('change', async () => {
    ++sequence; clearResults(); notice.textContent = 'Đang tải phạm vi dữ liệu…';
    try { await scope(org.value); await load(1); } catch (error) { notice.textContent = error.message; }
  });
  previous?.addEventListener('click', () => load(Math.max(1, page - 1)));
  next?.addEventListener('click', () => load(page + 1));
  const exportButton = document.getElementById('export');
  exportButton?.addEventListener('click', async () => {
    exportButton.disabled = true; notice.textContent = 'Đang chuẩn bị CSV…';
    try {
      const params = query(values(), 'reports', 1);
      const response = await fetchResponse(context + '/api/reports/export?' + params, 'text/csv');
      const blob = await response.blob(), url = URL.createObjectURL(blob), link = node('a');
      link.href = url; link.download = 'ticketscenter-' + params.get('metric').toLowerCase() + '.csv';
      document.body.append(link); link.click(); link.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000);
      notice.textContent = 'Đã tải CSV gồm toàn bộ dòng theo bộ lọc.';
    } catch (error) { notice.textContent = error.message; }
    finally { exportButton.disabled = false; }
  });
  (async () => {
    try {
      const today = new Date(); today.setUTCHours(0, 0, 0, 0);
      const start = new Date(today); start.setUTCDate(start.getUTCDate() - 30);
      const end = new Date(today); end.setUTCDate(end.getUTCDate() + 1);
      if (form.elements.from) form.elements.from.value = start.toISOString().slice(0, 19);
      if (form.elements.to) form.elements.to.value = end.toISOString().slice(0, 19);
      await scope(app.dataset.organization || '');
      if (!isAdmin && org.value && event) await scope(org.value);
      await load(1);
    } catch (error) { notice.textContent = error.message; }
  })();
})();
