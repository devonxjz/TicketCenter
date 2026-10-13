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
  const state=value=>({DRAFT:'Bản nháp',CONFIRMED:'Đã chốt',PAID:'Đã chi trả',PENDING:'Đang chờ',SUCCEEDED:'Thành công',FAILED:'Thất bại'}[value]||value);
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
    let summary = null, generation = 0, payoutKey = null, blockers = [], busy = false, loading = false, mutationEvent = null;
    let historyButtons = [];
    const updateButtons = () => {
      select.disabled = busy;
      for (const input of form.querySelectorAll('input')) input.disabled = busy;
      recalc.disabled = busy || loading || !summary || summary.status !== 'DRAFT';
      confirm.disabled = busy || loading || !summary?.settlementId || summary.status !== 'DRAFT' || blockers.length > 0 || !summary.endTime || new Date(summary.endTime) > new Date();
      pay.disabled = busy || loading || app.dataset.payoutMode !== 'fixture-simulated' || !summary?.settlementId || summary.status !== 'CONFIRMED' || BigInt(summary.availableToPay || '0') <= 0n || BigInt(summary.pendingAmount || '0') > 0n;
      reconcile.disabled = busy || loading || !payoutKey || !summary?.settlementId;
      for (const button of historyButtons) button.disabled = busy || loading;
    };
    async function load() {
      const own = ++generation, eventId = select.value;
      loading = true; summary = null; blockers = []; historyButtons = []; updateButtons(); document.getElementById('summary').replaceChildren(); document.getElementById('blockers').replaceChildren(); document.getElementById('payouts').replaceChildren();
      if (!eventId) { loading = false; updateButtons(); return true; }
      try {
        const [next, blocked] = await Promise.all([api('/api/admin/events/' + eventId + '/settlement'), api('/api/admin/events/' + eventId + '/settlement-blockers')]);
        if (own !== generation) return false; summary = next; blockers = blocked.items;
        for (const [label, value] of [['Trạng thái',state(next.status)],['Doanh thu',money(next.grossRevenue)],['Đã hoàn vé',money(next.totalRefund)],['Hoa hồng',money(next.totalCommission)],['Thực trả',money(next.netPayable)],['Đã chi trả',money(next.paidAmount)],['Đang chờ',money(next.pendingAmount)],['Có thể chi trả',money(next.availableToPay)]]) {
          const card = document.createElement('div'), dt = document.createElement('dt'), dd = document.createElement('dd'); card.className = 'finance-stat'; dt.textContent = label; dd.textContent = value; card.append(dt, dd); document.getElementById('summary').append(card);
        }
        for (const item of blocked.items) { const li = document.createElement('li'); li.textContent = item.detail; document.getElementById('blockers').appendChild(li); }
        if (!blocked.items.length) { const li = document.createElement('li'); li.textContent = 'Không còn giao dịch cần giải quyết.'; document.getElementById('blockers').appendChild(li); }
        if (next.settlementId) {
          const history = await api('/api/admin/settlements/' + next.settlementId + '/payouts');
          if (own !== generation) return false;
          reconcile.hidden = !history.some(item => item.payoutId === payoutKey && item.status === 'PENDING');
          for (const item of history) {
            const tr = document.createElement('tr'); [date(item.processedAt),money(item.amount),item.verificationState === 'UNKNOWN' ? 'Đang xác minh' : state(item.status),item.providerReference].forEach(value => cell(tr, value));
            if (item.status === 'PENDING') {
              const button = document.createElement('button'); button.type = 'button'; button.textContent = 'Xác minh lại kết quả đang chờ';
              button.addEventListener('click', () => {
                if (button.disabled || select.value !== eventId || summary?.settlementId !== next.settlementId) return;
                reconcilePayout(next.settlementId,item.payoutId);
              });
              historyButtons.push(button); tr.lastElementChild.appendChild(button);
            }
            document.getElementById('payouts').appendChild(tr);
          }
        }
        return true;
      } catch (error) { if (own === generation) { summary = null; tell(error.message); } return false; }
      finally { if (own === generation) { loading = false; updateButtons(); } }
    }
    async function mutate(action) {
      if (busy) return;
      busy = true; mutationEvent = select.value; updateButtons();
      let message;
      try { message = await action(); } catch (error) { message = error.message; }
      finally {
        // A lost response may still have committed a reservation. Reload its durable history before unlocking.
        const refreshed = await load();
        busy = false; mutationEvent = null; updateButtons();
        if (refreshed && message) tell(message);
      }
    }
    function reconcilePayout(settlementId,key) {
      return mutate(async () => {
        const result = await api('/api/admin/settlements/' + settlementId + '/payouts/' + key + '/reconcile',{},key);
        if (result.status !== 'PENDING' && payoutKey === key) { payoutKey = null; reconcile.hidden = true; }
        return result.status === 'PENDING' ? 'Kết quả vẫn đang chờ xác minh.' : 'Đã xác minh và cập nhật kết quả chi trả.';
      });
    }
    select.addEventListener('change', () => {
      if (busy) { select.value = mutationEvent; return; }
      payoutKey = null; reconcile.hidden = true; tell(''); load();
    });
    recalc.addEventListener('click', () => {
      if (recalc.disabled) return;
      const eventId = select.value;
      mutate(async () => { await api('/api/admin/events/' + eventId + '/settlement/recalculate',{},crypto.randomUUID()); return 'Đã tính lại bảng kê.'; });
    });
    confirm.addEventListener('click', () => {
      if (confirm.disabled) return;
      const settlementId = summary.settlementId;
      mutate(async () => { await api('/api/admin/settlements/' + settlementId + '/confirm',{},crypto.randomUUID()); return 'Đã chốt và đóng băng bảng kê.'; });
    });
    form.addEventListener('input', () => { if (!busy) payoutKey = null; });
    form.addEventListener('submit', event => {
      event.preventDefault();
      if (busy || pay.disabled || !summary?.settlementId) return;
      const settlementId = summary.settlementId;
      const payload = Object.fromEntries(['amount','bankAccountNumber','bankName','accountHolderName','note'].map(name => [name,form.elements[name].value]));
      mutate(async () => {
        payoutKey ||= crypto.randomUUID();
        const result = await api('/api/admin/settlements/' + settlementId + '/payouts',payload,payoutKey);
        if (result.status === 'PENDING') { reconcile.hidden = false; return 'Kết quả chi trả chưa xác định. Giữ nguyên lệnh để xác minh; không tạo lệnh thay thế.'; }
        payoutKey = null; reconcile.hidden = true;
        return result.status === 'SUCCEEDED' ? 'Đã ghi nhận chi trả mô phỏng.' : 'Lệnh chi trả thất bại; số dư đã được giải phóng.';
      });
    });
    reconcile.addEventListener('click', () => {
      if (reconcile.disabled || !payoutKey || !summary?.settlementId) return;
      reconcilePayout(summary.settlementId,payoutKey);
    });
  }
})();
