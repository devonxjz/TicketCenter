'use strict';
(() => {
  const app = document.getElementById('finance-app');
  if (!app) return;
  const context = app.dataset.context;
  const notice = document.getElementById('notice');
  const csrf = app.dataset.csrf;
  const translations = {
    UNAUTHENTICATED: 'Phiên đăng nhập đã hết hiệu lực. Vui lòng đăng nhập lại.',
    FORBIDDEN: 'Bạn không có quyền thực hiện thao tác này.',
    ADMIN_REQUIRED: 'Thao tác này cần quyền quản trị viên.',
    RULE_ALREADY_ATTACHED_TO_EVENTS: 'Chính sách đã gắn với sự kiện và không thể sửa.',
    SETTLEMENT_BLOCKED: 'Còn giao dịch chưa giải quyết. Hãy xử lý trước khi chốt.',
    EVENT_NOT_ENDED: 'Sự kiện chưa kết thúc.',
    PAYOUT_EXCEEDS_BALANCE: 'Số tiền vượt quá số dư có thể chi trả.',
    PAYOUT_RECONCILIATION_REQUIRED: 'Có lệnh chưa xác định kết quả. Cần xác minh trước khi tạo lệnh mới.',
    PAYOUT_PROVIDER_UNAVAILABLE: 'Dịch vụ chi trả chưa sẵn sàng.',
    PAYOUT_TERMS_CONFLICT: 'Lệnh này đã được lưu với thông tin khác.',
    DATABASE_UNAVAILABLE: 'Dữ liệu tạm thời chưa tải được. Vui lòng thử lại.'
  };
  const tell = (text) => { notice.textContent = text; };
  const money = (value) => value == null ? '—' : BigInt(value).toLocaleString('vi-VN');
  const date = (value) => value ? new Date(value).toLocaleString('vi-VN', { timeZone: 'UTC' }) + ' UTC' : '—';
  const cell = (tr, value) => { const td = document.createElement('td'); td.textContent = String(value ?? '—'); tr.appendChild(td); return td; };
  async function api(path, body, key) {
    const response = await fetch(context + path, {
      method: body === undefined ? 'GET' : 'POST', credentials: 'same-origin',
      headers: body === undefined ? {} : { 'Content-Type': 'application/json', 'X-CSRF-Token': csrf, 'Idempotency-Key': key },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    const result = await response.json();
    if (!response.ok) throw new Error(translations[result.error?.code] || 'Không thể hoàn tất yêu cầu. Kiểm tra dữ liệu và thử lại.');
    return result.data;
  }
  const withBusy = async (button, action) => {
    button.disabled = true;
    try { await action(); } catch (error) { tell(error.message); } finally {
      if (!['recalculate','confirm','payout-submit'].includes(button.id)) button.disabled = false;
    }
  };
  if (app.dataset.page === 'commission') {
    const select = document.getElementById('organization');
    const form = document.getElementById('commission-form');
    let generation = 0, key = null;
    const reset = () => { form.reset(); document.getElementById('form-title').textContent = 'Thêm chính sách'; key = null; };
    async function load() {
      const own = ++generation;
      document.getElementById('rules').replaceChildren(); reset();
      if (!select.value) return;
      try {
        const rows = await api('/api/admin/organizations/' + select.value + '/commission-rules');
        if (own !== generation) return;
        for (const rule of rows) {
          const tr = document.createElement('tr');
          [rule.ratePercent, money(rule.fixedFee), date(rule.effectiveFrom), date(rule.effectiveTo), rule.associatedEventsCount].forEach(value => cell(tr, value));
          const edit = document.createElement('button'); edit.type = 'button'; edit.textContent = Number(rule.associatedEventsCount) ? 'Đã khóa' : 'Sửa'; edit.disabled = Number(rule.associatedEventsCount) > 0;
          edit.addEventListener('click', () => {
            form.elements.ruleId.value = rule.ruleId;
            form.elements.ratePercent.value = rule.ratePercent; form.elements.fixedFee.value = rule.fixedFee;
            form.elements.effectiveFrom.value = rule.effectiveFrom.slice(0,19); form.elements.effectiveTo.value = rule.effectiveTo.slice(0,19);
            document.getElementById('form-title').textContent = 'Sửa chính sách'; key = null;
          });
          cell(tr, '').appendChild(edit); document.getElementById('rules').appendChild(tr);
        }
      } catch (error) { if (own === generation) tell(error.message); }
    }
    select.addEventListener('change', () => { tell(''); load(); });
    document.getElementById('cancel-edit').addEventListener('click', reset);
    form.addEventListener('input', () => { key = null; });
    form.addEventListener('submit', event => {
      event.preventDefault(); if (!select.value) { tell('Hãy chọn tổ chức.'); return; }
      withBusy(form.querySelector('[type=submit]'), async () => {
        key ||= crypto.randomUUID();
        const payload = Object.fromEntries(['ratePercent','fixedFee'].map(name => [name, form.elements[name].value]));
        for (const name of ['effectiveFrom','effectiveTo']) payload[name] = form.elements[name].value + (form.elements[name].value.length === 16 ? ':00Z' : 'Z');
        const path = form.elements.ruleId.value ? '/api/admin/commission-rules/' + form.elements.ruleId.value + '/edit' : '/api/admin/organizations/' + select.value + '/commission-rules';
        await api(path, payload, key); await load(); tell('Đã lưu chính sách.');
      });
    });
  } else {
    const select = document.getElementById('event');
    const form = document.getElementById('payout-form');
    const recalc = document.getElementById('recalculate'), confirm = document.getElementById('confirm'), pay = document.getElementById('payout-submit'), reconcile = document.getElementById('reconcile');
    let summary = null, generation = 0, payoutKey = null, pendingTerms = null;
    const updateButtons = blockers => {
      recalc.disabled = !summary || summary.status !== 'DRAFT';
      confirm.disabled = !summary?.settlementId || summary.status !== 'DRAFT' || blockers.length > 0 || !summary.endTime || new Date(summary.endTime) > new Date();
      pay.disabled = app.dataset.payoutMode !== 'fixture-simulated' || !summary?.settlementId || summary.status !== 'CONFIRMED' || BigInt(summary.availableToPay || '0') <= 0n || BigInt(summary.pendingAmount || '0') > 0n;
    };
    async function load() {
      const own = ++generation, eventId = select.value;
      summary = null; updateButtons([]); document.getElementById('summary').replaceChildren(); document.getElementById('blockers').replaceChildren(); document.getElementById('payouts').replaceChildren();
      if (!eventId) return;
      try {
        const [next, blocked] = await Promise.all([api('/api/admin/events/' + eventId + '/settlement'), api('/api/admin/events/' + eventId + '/settlement-blockers')]);
        if (own !== generation) return; summary = next;
        for (const [label, value] of [['Trạng thái',next.status],['Doanh thu',money(next.grossRevenue)],['Đã hoàn vé',money(next.totalRefund)],['Hoa hồng',money(next.totalCommission)],['Thực trả',money(next.netPayable)],['Đã chi trả',money(next.paidAmount)],['Đang chờ',money(next.pendingAmount)],['Có thể chi trả',money(next.availableToPay)]]) {
          const dt = document.createElement('dt'), dd = document.createElement('dd'); dt.textContent = label; dd.textContent = value; document.getElementById('summary').append(dt, dd);
        }
        for (const item of blocked.items) { const li = document.createElement('li'); li.textContent = item.detail; document.getElementById('blockers').appendChild(li); }
        if (!blocked.items.length) { const li = document.createElement('li'); li.textContent = 'Không còn giao dịch cần giải quyết.'; document.getElementById('blockers').appendChild(li); }
        updateButtons(blocked.items);
        if (next.settlementId) {
          const history = await api('/api/admin/settlements/' + next.settlementId + '/payouts');
          if (own !== generation) return;
          for (const item of history) { const tr = document.createElement('tr'); [date(item.processedAt),money(item.amount),item.verificationState === 'UNKNOWN' ? 'Đang xác minh' : item.status,item.providerReference].forEach(value => cell(tr, value)); document.getElementById('payouts').appendChild(tr); }
        }
      } catch (error) { if (own === generation) tell(error.message); }
    }
    select.addEventListener('change', () => { payoutKey = null; pendingTerms = null; reconcile.hidden = true; tell(''); load(); });
    recalc.addEventListener('click', () => withBusy(recalc, async () => { await api('/api/admin/events/' + select.value + '/settlement/recalculate',{},crypto.randomUUID()); await load(); tell('Đã tính lại bảng kê.'); }));
    confirm.addEventListener('click', () => withBusy(confirm, async () => { await api('/api/admin/settlements/' + summary.settlementId + '/confirm',{},crypto.randomUUID()); await load(); tell('Đã chốt và đóng băng bảng kê.'); }));
    form.addEventListener('input', () => { if (!pendingTerms) payoutKey = null; });
    form.addEventListener('submit', event => {
      event.preventDefault();
      if (pay.disabled || !summary?.settlementId) return;
      withBusy(pay, async () => {
        payoutKey ||= crypto.randomUUID();
        const payload = pendingTerms || Object.fromEntries(['amount','bankAccountNumber','bankName','accountHolderName','note'].map(name => [name,form.elements[name].value]));
        pendingTerms = payload;
        reconcile.hidden = false;
        const result = await api('/api/admin/settlements/' + summary.settlementId + '/payouts',payload,payoutKey);
        if (result.status === 'PENDING') { pendingTerms = payload; reconcile.hidden = false; tell('Kết quả chi trả chưa xác định. Giữ nguyên lệnh để xác minh; không tạo lệnh thay thế.'); }
        else { pendingTerms = null; payoutKey = null; reconcile.hidden = true; tell(result.status === 'SUCCEEDED' ? 'Đã ghi nhận chi trả mô phỏng.' : 'Lệnh chi trả thất bại; số dư đã được giải phóng.'); }
        await load();
      });
    });
    reconcile.addEventListener('click', () => withBusy(reconcile, async () => {
      if (!pendingTerms || !payoutKey || !summary?.settlementId) return;
      const result = await api('/api/admin/settlements/' + summary.settlementId + '/payouts/' + payoutKey + '/reconcile',pendingTerms,payoutKey);
      if (result.status !== 'PENDING') { pendingTerms = null; payoutKey = null; reconcile.hidden = true; }
      await load(); tell(result.status === 'PENDING' ? 'Kết quả vẫn đang chờ xác minh.' : 'Đã xác minh và cập nhật kết quả chi trả.');
    }));
  }
})();
